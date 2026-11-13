package com.base.admin.claim.service;

import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.service.AiChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 用双向自然语言推理判断新投诉原文和历史现象原文是不是同一现象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimPhenomenonNli {

    private static final int BATCH = 12;

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public boolean available() {
        return aiChatService.isEnabled();
    }

    public NliJudgement judge(String claimOriginal, List<String> phenomena, Consumer<String> progress) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (phenomena != null) {
            for (String phenomenon : phenomena) {
                String text = ClaimTexts.empty(phenomenon);
                if (!text.isBlank()) {
                    unique.add(text);
                }
            }
        }
        if (!available() || ClaimTexts.blank(claimOriginal) || unique.isEmpty()) {
            return new NliJudgement(false, Map.of());
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>(unique);
        boolean any = false;
        int batches = (pending.size() + BATCH - 1) / BATCH;
        for (int offset = 0; offset < pending.size(); offset += BATCH) {
            int index = offset / BATCH + 1;
            List<String> batch = pending.subList(offset, Math.min(offset + BATCH, pending.size()));
            try {
                scores.putAll(ask(claimOriginal, batch));
                any = true;
                say(progress, ClaimTexts.progressBar("正在对照历史案例", index, batches));
            } catch (Exception ex) {
                log.warn("双向 NLI 本批失败: {}", ex.getMessage());
            }
        }
        return new NliJudgement(any, scores);
    }

    private void say(Consumer<String> progress, String text) {
        if (progress == null || ClaimTexts.blank(text)) {
            return;
        }
        try {
            progress.accept(text);
        } catch (Exception ex) {
            log.warn("理赔进度回调失败: {}", ex.getMessage());
        }
    }

    private Map<String, Double> ask(String claimOriginal, List<String> batch) throws Exception {
        StringBuilder listed = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            listed.append(i + 1).append(". ").append(batch.get(i)).append("\n");
        }
        String system = """
                你是自然语言推理模型，只判断投诉现象是否为同一件事，做双向蕴含。
                忽略顾客、电话、门店、日期、数量、金额和诉求。历史现象里的「服务-」「产品-」「物流-」只是分类前缀。
                ab 表示新投诉原文里的问题是否蕴含该历史现象，ba 表示该历史现象是否蕴含新投诉里的问题。
                同一现象的不同说法，两边都标 entailment。只有单边包含或仅仅沾边时，一边 entailment、一边 neutral。
                不是同一件事标 contradiction 或 neutral。置信度 ap、bp 取 0 到 1。
                只输出 JSON 数组，不要解释。每项格式：{"i":1,"ab":"entailment","ap":0.95,"ba":"entailment","bp":0.95}
                ab 和 ba 只能是 entailment、neutral、contradiction。
                """;
        String user = "新投诉原文：\n" + ClaimTexts.clip(claimOriginal, 1200) + "\n\n历史现象原文：\n" + listed;
        String raw = aiChatService.chat(system, user, null, 0.0);
        return parse(raw, batch);
    }

    private Map<String, Double> parse(String raw, List<String> batch) throws Exception {
        int start = raw == null ? -1 : raw.indexOf('[');
        int end = raw == null ? -1 : raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("双向 NLI 没有返回 JSON 数组");
        }
        JsonNode rows = objectMapper.readTree(raw.substring(start, end + 1));
        if (!rows.isArray()) {
            throw new IllegalStateException("双向 NLI 返回的不是数组");
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            int index = row.path("i").asInt(0);
            if (index < 1 || index > batch.size()) {
                continue;
            }
            String forward = row.path("ab").asText("");
            String backward = row.path("ba").asText("");
            double forwardProbability = probability(row, "ap", forward);
            double backwardProbability = probability(row, "bp", backward);
            scores.put(batch.get(index - 1), ClaimTexts.bidirectionalEquivalence(
                    forward, forwardProbability, backward, backwardProbability));
        }
        return scores;
    }

    private double probability(JsonNode row, String field, String label) {
        JsonNode node = row.get(field);
        if (node != null && node.isNumber()) {
            return node.asDouble();
        }
        if ("entailment".equals(ClaimTexts.nliLabel(label))) {
            return 0.9;
        }
        return 0;
    }

    public record NliJudgement(boolean completed, Map<String, Double> scores) {
        public double scoreOf(String phenomenon) {
            if (!completed || scores == null) {
                return 0;
            }
            return scores.getOrDefault(ClaimTexts.empty(phenomenon), 0.0);
        }
    }
}
