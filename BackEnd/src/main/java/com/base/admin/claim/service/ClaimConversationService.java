package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimEvidence;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.domain.ClaimMessage;
import com.base.admin.claim.domain.ClaimSopNode;
import com.base.admin.claim.mapper.ClaimCaseMapper;
import com.base.admin.claim.mapper.ClaimEvidenceMapper;
import com.base.admin.claim.mapper.ClaimHistoryCaseMapper;
import com.base.admin.claim.mapper.ClaimMessageMapper;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.service.AiChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimConversationService {

    private final ClaimCaseMapper caseMapper;
    private final ClaimMessageMapper messageMapper;
    private final ClaimEvidenceMapper evidenceMapper;
    private final ClaimHistoryCaseMapper historyMapper;
    private final ClaimSopService sopService;
    private final ClaimFileStorage fileStorage;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public record ClaimFile(String kind, String fileName, byte[] bytes, String error) {
    }

    public record ClaimInbound(String conversationId, String conversationType, String senderId, String senderName,
                               String text, String msgType, List<ClaimFile> files) {
    }

    @Transactional
    public String handle(ClaimInbound inbound) {
        if ("2".equals(inbound.conversationType())) {
            return "请在和理赔机器人的单聊里发送投诉，不要发在群里。";
        }
        String conversationId = ClaimTexts.empty(inbound.conversationId());
        if (conversationId.isBlank()) {
            return "没有拿到会话，请再发一次。";
        }
        synchronized (conversationId.intern()) {
            return handleLocked(inbound, conversationId);
        }
    }

    @Transactional
    public ClaimCase recommendAgain(Long caseId) {
        ClaimCase claim = caseMapper.selectById(caseId);
        if (claim == null) {
            return null;
        }
        String reply = finish(claim);
        saveMessage(claim.getId(), "bot", "text", reply);
        return caseMapper.selectById(caseId);
    }

    private String handleLocked(ClaimInbound inbound, String conversationId) {
        String text = ClaimTexts.empty(inbound.text());
        if (ClaimTexts.isAbortPhrase(text)) {
            return abort(inbound, conversationId, text);
        }
        boolean fresh = text.contains("新投诉") || text.contains("新的投诉") || text.contains("再录一单");
        ClaimCase claim = current(conversationId);
        if (claim != null && fresh) {
            claim.setStatus(ClaimCase.INTERRUPTED);
            caseMapper.updateById(claim);
            claim = null;
        }
        boolean created = false;
        if (claim == null || ClaimCase.RECOMMENDED.equals(claim.getStatus()) || ClaimCase.INTERRUPTED.equals(claim.getStatus())) {
            claim = create(inbound, conversationId);
            created = true;
        }
        List<String> saved = saveFiles(claim.getId(), inbound.files());
        String userText = text;
        if (userText.isBlank() && !saved.isEmpty()) {
            userText = "已发送证据：" + String.join("、", saved);
        }
        if (!userText.isBlank()) {
            saveMessage(claim.getId(), "user", inbound.msgType() == null ? "text" : inbound.msgType(), userText);
        }
        if (!ClaimTexts.blank(inbound.senderName())) {
            claim.setSenderName(inbound.senderName());
        }
        applyFacts(claim, userText, !saved.isEmpty());
        fillCategory(claim);
        if (created && saved.isEmpty() && !hasClaimFact(claim)) {
            discard(claim.getId());
            return openingScript();
        }
        String reply;
        if (ready(claim)) {
            reply = finish(claim);
        } else {
            String packed = nextQuestion(claim);
            claim.setPendingSlot(packed == null ? "" : pendingOf(packed));
            caseMapper.updateById(claim);
            String question = packed == null ? "请再补充一点投诉信息。" : packed.substring(packed.indexOf('|') + 1);
            reply = collectedLine(claim) + question;
        }
        reply = appendFileErrors(reply, inbound.files());
        saveMessage(claim.getId(), "bot", "text", reply);
        return reply;
    }

    private String abort(ClaimInbound inbound, String conversationId, String text) {
        ClaimCase claim = current(conversationId);
        if (claim == null) {
            return "当前没有进行中的投诉。要记录时，直接发投诉内容。";
        }
        if (!text.isBlank()) {
            saveMessage(claim.getId(), "user", inbound.msgType() == null ? "text" : inbound.msgType(), text);
        }
        saveFiles(claim.getId(), inbound.files());
        claim.setStatus(ClaimCase.INTERRUPTED);
        claim.setPendingSlot("");
        caseMapper.updateById(claim);
        String reply = "这张投诉已中断，不再继续追问。需要重新记录时，直接发投诉内容，或回复「新投诉」。";
        saveMessage(claim.getId(), "bot", "text", reply);
        return reply;
    }

    private void discard(Long caseId) {
        List<ClaimMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, caseId));
        for (ClaimMessage message : messages) {
            messageMapper.deleteById(message.getId());
        }
        caseMapper.deleteById(caseId);
    }

    private String openingScript() {
        return "这句话里没有识别到投诉信息。请按这个说法发，尽量带上顾客、购买时间、门店、产品和具体问题：\n"
                + "顾客张三反馈上周在克西路店买的羊肉太硬了。";
    }

    private boolean hasClaimFact(ClaimCase claim) {
        return !ClaimTexts.blank(claim.getCustomerName())
                || !ClaimTexts.blank(claim.getStoreName())
                || !ClaimTexts.blank(claim.getProductName())
                || !ClaimTexts.blank(claim.getSymptom())
                || !ClaimTexts.blank(claim.getPurchaseDate())
                || !ClaimTexts.blank(claim.getCustomerDemand())
                || !ClaimTexts.blank(claim.getProductCategory())
                || !ClaimTexts.blank(claim.getLogisticsSummary())
                || !ClaimTexts.blank(claim.getCookingMethod());
    }

    /** 这句话里能确定的姓名、门店、产品、现象和相对日期，以这句话为准。 */
    private void normalizeFacts(ClaimCase claim, String text) {
        String name = ClaimTexts.customerNameOf(text);
        if (!name.isBlank()) {
            claim.setCustomerName(ClaimTexts.clip(name, 64));
        }
        String store = ClaimTexts.storeNameOf(text);
        if (!store.isBlank()) {
            claim.setStoreName(ClaimTexts.clip(store, 128));
        }
        String product = ClaimTexts.productNameOf(text);
        if (!product.isBlank()) {
            claim.setProductName(ClaimTexts.clip(product, 255));
        }
        String symptom = symptomOf(text);
        if (!symptom.isBlank()) {
            claim.setSymptom(ClaimTexts.clip(symptom, 255));
        }
        String date = ClaimTexts.purchaseDateOf(text, today());
        if (!date.isBlank()) {
            claim.setPurchaseDate(date);
        }
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }

    private ClaimCase current(String conversationId) {
        return caseMapper.selectOne(new LambdaQueryWrapper<ClaimCase>()
                .eq(ClaimCase::getConversationId, conversationId)
                .eq(ClaimCase::getStatus, ClaimCase.COLLECTING)
                .orderByDesc(ClaimCase::getId)
                .last("limit 1"));
    }

    private ClaimCase create(ClaimInbound inbound, String conversationId) {
        ClaimCase claim = new ClaimCase();
        claim.setConversationId(conversationId);
        claim.setSenderId(ClaimTexts.empty(inbound.senderId()));
        claim.setSenderName(ClaimTexts.empty(inbound.senderName()));
        claim.setStatus(ClaimCase.COLLECTING);
        claim.setCustomerName("");
        claim.setCustomerPhone("");
        claim.setCustomerAddress("");
        claim.setMemberNo("");
        claim.setCustomerType("");
        claim.setConsumeSummary("");
        claim.setProductCategory("");
        claim.setProductName("");
        claim.setProductSpec("");
        claim.setStoreName("");
        claim.setPurchaseDate("");
        claim.setOrderNo("");
        claim.setLogisticsNo("");
        claim.setLogisticsSummary("");
        claim.setSymptom("");
        claim.setSymptomCode("");
        claim.setCookingMethod("");
        claim.setCustomerDemand("");
        claim.setPendingSlot("");
        claim.setSkippedSlots("");
        claim.setSopTitle("");
        claim.setRecommendation("");
        claim.setRecommendationBasis("");
        claim.setSimilarCaseIds("");
        caseMapper.insert(claim);
        return claim;
    }

    private List<String> saveFiles(Long caseId, List<ClaimFile> files) {
        List<String> names = new ArrayList<>();
        if (files == null) {
            return names;
        }
        for (ClaimFile file : files) {
            if (file.bytes() == null || file.bytes().length == 0) {
                continue;
            }
            String name = ClaimTexts.blank(file.fileName()) ? file.kind() : file.fileName();
            String path = fileStorage.save(caseId, name, file.bytes());
            ClaimEvidence evidence = new ClaimEvidence();
            evidence.setCaseId(caseId);
            evidence.setFileType(ClaimTexts.empty(file.kind()).isBlank() ? "file" : file.kind());
            evidence.setFileName(name);
            evidence.setStoragePath(path);
            evidence.setFileSize((long) file.bytes().length);
            evidenceMapper.insert(evidence);
            names.add(name);
        }
        return names;
    }

    private void applyFacts(ClaimCase claim, String text, boolean hasFile) {
        String slot = ClaimTexts.empty(claim.getPendingSlot());
        if (ClaimTexts.isSkipPhrase(text) && !slot.isBlank()) {
            claim.setSkippedSlots(ClaimTexts.addSkip(claim.getSkippedSlots(), slot));
            return;
        }
        if (!text.isBlank()) {
            applyAi(claim, text);
            extractLocal(claim, text);
            normalizeFacts(claim, text);
        }
        if ("evidence".equals(slot) && hasFile) {
            return;
        }
        if (!slot.isBlank() && !text.isBlank() && !filled(claim, slot)) {
            writeSlot(claim, slot, text);
        }
    }

    /** 一句话里可能同时有姓名、电话、门店、产品和现象，先拆开再决定下一问。 */
    private void extractLocal(ClaimCase claim, String text) {
        setPhone(claim, ClaimTexts.phoneOf(text));
        setIfBlank(claim::getCustomerAddress, claim::setCustomerAddress, ClaimTexts.addressOf(text));
        setIfBlank(claim::getCustomerName, claim::setCustomerName, nameOf(text));
        setIfBlank(claim::getStoreName, claim::setStoreName, storeOf(text));
        setIfBlank(claim::getProductName, claim::setProductName, productOf(text));
        setIfBlank(claim::getSymptom, claim::setSymptom, symptomOf(text));
        setIfBlank(claim::getCustomerType, claim::setCustomerType, customerType(text));
        setIfBlank(claim::getCustomerDemand, claim::setCustomerDemand, demandOf(text));
        setIfBlank(claim::getCookingMethod, claim::setCookingMethod, cookingOf(text));
        setIfBlank(claim::getConsumeSummary, claim::setConsumeSummary, consumeOf(text));
        setIfBlank(claim::getLogisticsSummary, claim::setLogisticsSummary, logisticsOf(text));
        String logistics = text.replaceAll(".*?(SF\\d{8,}).*", "$1");
        if (ClaimTexts.blank(claim.getLogisticsNo()) && logistics.startsWith("SF")) {
            claim.setLogisticsNo(logistics);
        }
    }

    private void writeSlot(ClaimCase claim, String slot, String text) {
        switch (slot) {
            case "customerPhone" -> setPhone(claim, ClaimTexts.phoneOf(text));
            case "customerName" -> setIfBlank(claim::getCustomerName, claim::setCustomerName,
                    ClaimTexts.customerNameOf(text).isBlank() && text.length() <= 8 ? text : ClaimTexts.customerNameOf(text));
            case "storeName" -> setIfBlank(claim::getStoreName, claim::setStoreName,
                    ClaimTexts.storeNameOf(text).isBlank() && text.length() <= 16 ? text : ClaimTexts.storeNameOf(text));
            case "productName" -> setIfBlank(claim::getProductName, claim::setProductName,
                    ClaimTexts.productNameOf(text).isBlank() && text.length() <= 16 ? text : ClaimTexts.productNameOf(text));
            case "symptom" -> setIfBlank(claim::getSymptom, claim::setSymptom,
                    symptomOf(text).isBlank() && text.length() <= 16 ? text : symptomOf(text));
            case "customerDemand" -> setIfBlank(claim::getCustomerDemand, claim::setCustomerDemand, text);
            case "customerType" -> setIfBlank(claim::getCustomerType, claim::setCustomerType, customerType(text).isBlank() ? text : customerType(text));
            case "consumeSummary" -> setIfBlank(claim::getConsumeSummary, claim::setConsumeSummary, text);
            case "cookingMethod" -> setIfBlank(claim::getCookingMethod, claim::setCookingMethod, text);
            case "logisticsSummary" -> setIfBlank(claim::getLogisticsSummary, claim::setLogisticsSummary, text);
            default -> {
            }
        }
    }

    private void applyAi(ClaimCase claim, String latest) {
        if (!aiChatService.isEnabled() || latest.isBlank()) {
            return;
        }
        String system = """
                你是理赔信息抽取器。只分析用户最新这一句话里已经说出的事实。
                没提到的字段用空字符串，不要编造，不要把整句原文塞进一个字段。
                和投诉无关的寒暄，所有字段都留空。
                机器人之前的提问不算用户提供的信息。
                今天是 %s。上周、上星期指上一个自然周周一到周日，写成 YYYY-MM-DD～YYYY-MM-DD，不要保留「上周」。
                顾客只写姓名，不要带「反馈」。门店只写店名或平台。产品只写货品，不要带购买经过。
                现象写成「产品+问题」，例如羊肉太硬，不要只写太硬或肉太硬。
                示例：顾客张三反馈上周在克西路店买的羊肉太硬了
                → customerName=张三，storeName=克西路店，productName=羊肉，symptom=羊肉太硬，purchaseDate=%s。
                只输出一个 JSON 对象，字段为：customerName, customerPhone, customerAddress, memberNo, customerType, consumeSummary, productCategory, productName, productSpec, storeName, purchaseDate, orderNo, logisticsNo, logisticsSummary, symptom, cookingMethod, customerDemand。
                customerType 只能是 新客、老客 或空字符串。
                牛羊肉坏了、变质、不新鲜写成「肉坏了」，不要写成坏果。坏果只用于蔬菜和水果。
                """.formatted(today().toString(), ClaimTexts.purchaseDateOf("上周", today()));
        try {
            String raw = aiChatService.chat(system, "已记录：\n" + snapshot(claim) + "\n用户最新一句：\n" + latest, null, 0.1);
            JsonNode node = readJson(raw);
            if (node == null) {
                return;
            }
            setIfBlank(claim::getCustomerName, claim::setCustomerName, json(node, "customerName"));
            setPhone(claim, json(node, "customerPhone"));
            setIfBlank(claim::getCustomerAddress, claim::setCustomerAddress, json(node, "customerAddress"));
            setIfBlank(claim::getMemberNo, claim::setMemberNo, json(node, "memberNo"));
            String type = json(node, "customerType");
            if ("新客".equals(type) || "老客".equals(type)) {
                setIfBlank(claim::getCustomerType, claim::setCustomerType, type);
            }
            setIfBlank(claim::getConsumeSummary, claim::setConsumeSummary, json(node, "consumeSummary"));
            setIfBlank(claim::getProductCategory, claim::setProductCategory, json(node, "productCategory"));
            setIfBlank(claim::getProductName, claim::setProductName, json(node, "productName"));
            setIfBlank(claim::getProductSpec, claim::setProductSpec, json(node, "productSpec"));
            setIfBlank(claim::getStoreName, claim::setStoreName, json(node, "storeName"));
            setIfBlank(claim::getPurchaseDate, claim::setPurchaseDate, json(node, "purchaseDate"));
            setIfBlank(claim::getOrderNo, claim::setOrderNo, json(node, "orderNo"));
            setIfBlank(claim::getLogisticsNo, claim::setLogisticsNo, json(node, "logisticsNo"));
            setIfBlank(claim::getLogisticsSummary, claim::setLogisticsSummary, json(node, "logisticsSummary"));
            setIfBlank(claim::getSymptom, claim::setSymptom, json(node, "symptom"));
            setIfBlank(claim::getCookingMethod, claim::setCookingMethod, json(node, "cookingMethod"));
            setIfBlank(claim::getCustomerDemand, claim::setCustomerDemand, json(node, "customerDemand"));
        } catch (Exception ex) {
            log.warn("理赔字段抽取失败: {}", ex.getMessage());
        }
    }

    private boolean ready(ClaimCase claim) {
        return filled(claim, "customerPhone") && filled(claim, "customerName") && filled(claim, "storeName")
                && filled(claim, "productName") && filled(claim, "symptom") && filled(claim, "customerDemand")
                && filled(claim, "customerType") && filled(claim, "consumeSummary")
                && (!needCooking(claim) || filled(claim, "cookingMethod"))
                && (!needLogistics(claim) || filled(claim, "logisticsSummary"))
                && (!needEvidence(claim) || filled(claim, "evidence"));
    }

    private String nextQuestion(ClaimCase claim) {
        if (!filled(claim, "customerPhone")) return slot("customerPhone", "请发购买人手机号。没有就回复「没有」。");
        if (!filled(claim, "customerName")) return slot("customerName", "请发购买人姓名。");
        if (!filled(claim, "storeName")) return slot("storeName", "请发购买门店或平台，例如克西路店、天猫、京东、微商城。");
        if (!filled(claim, "productName")) return slot("productName", "请发产品名称，有重量或件数也一起说。");
        if (!filled(claim, "symptom")) return slot("symptom", "请说明具体问题，例如膻味、肉老、开化、缺斤、异物、坏果、发货地。");
        if (!filled(claim, "customerDemand")) return slot("customerDemand", "请说明顾客诉求：补发、退款、换货，或者索赔金额。");
        if (!filled(claim, "customerType")) return slot("customerType", "请说明这位顾客是新客还是老客。");
        if (!filled(claim, "consumeSummary")) return slot("consumeSummary", "请补充消费情况：大概买过几次，有没有买过同款。查不到就回复「没有」。");
        if (needCooking(claim) && !filled(claim, "cookingMethod")) return slot("cookingMethod", "请补充解冻方式和烹饪方式，例如清炖、红烧、火锅，以及炖了多久。");
        if (needLogistics(claim) && !filled(claim, "logisticsSummary")) return slot("logisticsSummary", "请补充物流单号、运输时长、是否开化或破损。没有单号就回复「没有」。");
        if (needEvidence(claim) && !filled(claim, "evidence")) return slot("evidence", "请在这个聊天里发照片或视频。没有就回复「没有照片」。");
        return null;
    }

    private String pendingOf(String question) {
        int split = question.indexOf('|');
        return split < 0 ? "" : question.substring(0, split);
    }

    private String slot(String code, String question) {
        return code + "|" + question;
    }

    private String finish(ClaimCase claim) {
        String blob = complaintBlob(claim);
        ClaimSopNode symptom = sopService.matchSymptom(blob);
        ClaimSopNode action = sopService.pickAction(symptom, claim);
        if (symptom != null) {
            claim.setSymptomCode(symptom.getNodeCode());
        }
        claim.setSopTitle(action == null ? "" : action.getTitle());
        List<ClaimHistoryCase> similar = similarCases(claim, symptom);
        claim.setSimilarCaseIds(String.join(",", similar.stream().map(item -> String.valueOf(item.getId())).toList()));
        String basis = basis(symptom, action, similar);
        String suggestion = suggest(claim, action, basis);
        claim.setRecommendation(suggestion);
        claim.setRecommendationBasis(basis);
        claim.setStatus(ClaimCase.RECOMMENDED);
        claim.setPendingSlot("");
        caseMapper.updateById(claim);
        return "这张投诉已收集完成。\n\n建议：\n" + suggestion + "\n\n依据：\n" + basis;
    }

    private String suggest(ClaimCase claim, ClaimSopNode action, String basis) {
        String fallback = action == null
                ? "档案已齐，但没有命中流程分支。请在后台核对现象后人工定方案。"
                : "按「" + action.getTitle() + "」处理：" + ClaimTexts.empty(action.getContent());
        if (!aiChatService.isEnabled()) {
            return fallback;
        }
        String system = """
                你是小巴依理赔助手。建议必须执行依据里的「SOP 处理」，不能改成别的现象或别的处理。
                参考案例只说明同一类现象以前怎么结案，不能用别的现象覆盖流程。
                牛羊肉坏了、变质、不新鲜，只能按肉品「颜色发黑、不新鲜」或物流变质处理，禁止写成蔬菜坏果，也禁止引用蔬菜案例。
                蔬菜水果坏了才用坏果。产品和参考案例品类不一致时，丢掉那条案例，不要写进建议。
                不要发明流程里没有的赔偿倍数。缺斤查实才写退一赔三，异物索赔才写十倍且不足按 1000 元。
                用中文，先写建议动作和货品或金额，再写一句原因，并点明依据的流程名称。不要输出 JSON。
                """;
        try {
            String answer = aiChatService.chat(system, "档案：\n" + snapshot(claim) + "\n\n" + basis, null, 0.2);
            return ClaimTexts.blank(answer) ? fallback : answer.trim();
        } catch (Exception ex) {
            log.warn("理赔建议生成失败: {}", ex.getMessage());
            return fallback;
        }
    }

    private String basis(ClaimSopNode symptom, ClaimSopNode action, List<ClaimHistoryCase> similar) {
        StringBuilder text = new StringBuilder();
        if (symptom != null) {
            text.append("SOP 现象：").append(symptom.getTitle()).append("。").append(ClaimTexts.empty(symptom.getContent())).append("\n");
        }
        if (action != null && (symptom == null || !action.getNodeCode().equals(symptom.getNodeCode()))) {
            text.append("SOP 处理：").append(action.getTitle()).append("。").append(ClaimTexts.empty(action.getContent())).append("\n");
        }
        if (similar.isEmpty()) {
            text.append("参考案例：没有找到同一现象的已结案记录。");
            return text.toString();
        }
        text.append("参考案例（与上面同一现象）：\n");
        int index = 1;
        for (ClaimHistoryCase item : similar) {
            text.append(index++).append(". ")
                    .append(ClaimTexts.empty(item.getComplaintDate())).append(" ")
                    .append(ClaimTexts.empty(item.getSymptomBrief())).append(" / ")
                    .append(ClaimTexts.empty(item.getProductName()))
                    .append(" → ").append(clipOpinion(item))
                    .append("（").append(ClaimTexts.empty(item.getJudgment())).append("）\n");
        }
        return text.toString().trim();
    }

    private String clipOpinion(ClaimHistoryCase item) {
        String opinion = ClaimTexts.empty(item.getClaimProduct());
        if (opinion.isBlank()) {
            opinion = ClaimTexts.empty(item.getClaimOpinion());
        }
        return ClaimTexts.clip(opinion.replace("\n", " "), 80);
    }

    private List<ClaimHistoryCase> similarCases(ClaimCase claim, ClaimSopNode symptom) {
        List<ClaimHistoryCase> all = historyMapper.selectList(new LambdaQueryWrapper<ClaimHistoryCase>()
                .orderByDesc(ClaimHistoryCase::getId)
                .last("limit 800"));
        String keywords = symptom == null ? "" : ClaimTexts.empty(symptom.getKeywords());
        return all.stream()
                .map(item -> new Scored(item, score(claim, keywords, item)))
                .filter(item -> item.score > 0)
                .sorted(Comparator.comparingInt((Scored item) -> item.score).reversed())
                .limit(3)
                .map(item -> item.row)
                .toList();
    }

    private int score(ClaimCase claim, String keywords, ClaimHistoryCase row) {
        String brief = ClaimTexts.empty(row.getSymptomBrief());
        int symptomScore = 0;
        for (String keyword : keywords.split(",")) {
            String word = keyword.trim();
            if (word.length() >= 1 && brief.contains(word)) {
                symptomScore += word.length();
            }
        }
        String claimSide = ClaimTexts.empty(claim.getProductCategory()) + ClaimTexts.empty(claim.getProductName()) + ClaimTexts.empty(claim.getSymptom());
        String rowSide = ClaimTexts.empty(row.getProductCategory()) + ClaimTexts.empty(row.getProductName()) + brief;
        boolean claimMeat = ClaimTexts.meatText(claimSide) && !ClaimTexts.produceText(claimSide);
        boolean rowProduce = ClaimTexts.produceText(rowSide) || brief.contains("坏果");
        boolean rowMeat = ClaimTexts.meatText(rowSide);
        if (claimMeat && rowProduce && !rowMeat) {
            return 0;
        }
        if (ClaimTexts.produceText(claimSide) && !ClaimTexts.meatText(claimSide) && rowMeat && !ClaimTexts.produceText(rowSide)) {
            return 0;
        }
        if (symptomScore == 0 && claimMeat && rowMeat && ClaimTexts.spoilageText(claimSide) && ClaimTexts.spoilageText(brief)) {
            symptomScore = 3;
        }
        if (symptomScore == 0) {
            return 0;
        }
        int score = symptomScore * 10;
        String category = ClaimTexts.empty(claim.getProductCategory());
        String product = ClaimTexts.empty(claim.getProductName());
        if (!category.isBlank() && ClaimTexts.empty(row.getProductCategory()).contains(category)) {
            score += 4;
        }
        if (!product.isBlank() && (ClaimTexts.empty(row.getProductName()).contains(product) || product.contains(ClaimTexts.empty(row.getProductName())))) {
            score += 3;
        }
        return score;
    }

    /** 只用已经落库的投诉事实匹配流程，不把机器人提问里的举例算进去。 */
    private String complaintBlob(ClaimCase claim) {
        return String.join("\n",
                ClaimTexts.empty(claim.getSymptom()),
                ClaimTexts.empty(claim.getProductName()),
                ClaimTexts.empty(claim.getCookingMethod()),
                ClaimTexts.empty(claim.getLogisticsSummary()),
                ClaimTexts.empty(claim.getConsumeSummary()));
    }

    private boolean filled(ClaimCase claim, String slot) {
        if (ClaimTexts.skipped(claim.getSkippedSlots(), slot)) {
            return true;
        }
        return switch (slot) {
            case "customerPhone" -> !ClaimTexts.blank(claim.getCustomerPhone());
            case "customerName" -> !ClaimTexts.blank(claim.getCustomerName());
            case "storeName" -> !ClaimTexts.blank(claim.getStoreName());
            case "productName" -> !ClaimTexts.blank(claim.getProductName());
            case "symptom" -> !ClaimTexts.blank(claim.getSymptom());
            case "customerDemand" -> !ClaimTexts.blank(claim.getCustomerDemand());
            case "customerType" -> !ClaimTexts.blank(claim.getCustomerType());
            case "consumeSummary" -> !ClaimTexts.blank(claim.getConsumeSummary());
            case "cookingMethod" -> !ClaimTexts.blank(claim.getCookingMethod());
            case "logisticsSummary" -> !ClaimTexts.blank(claim.getLogisticsSummary());
            case "evidence" -> evidenceMapper.selectCount(new LambdaQueryWrapper<ClaimEvidence>().eq(ClaimEvidence::getCaseId, claim.getId())) > 0;
            default -> true;
        };
    }

    private boolean needCooking(ClaimCase claim) {
        String code = symptomCode(claim);
        return code.startsWith("product.smell") || code.startsWith("product.fat") || code.startsWith("product.tough")
                || code.startsWith("product.bland") || code.startsWith("veg.taste");
    }

    private boolean needLogistics(ClaimCase claim) {
        String code = symptomCode(claim);
        return code.startsWith("logistics") || code.startsWith("service.origin");
    }

    private boolean needEvidence(ClaimCase claim) {
        String code = symptomCode(claim);
        return code.startsWith("product.fat") || code.startsWith("product.weight") || code.startsWith("product.color")
                || code.startsWith("product.foreign") || code.startsWith("veg") || code.startsWith("logistics");
    }

    private String symptomCode(ClaimCase claim) {
        if (!ClaimTexts.blank(claim.getSymptomCode())) {
            return claim.getSymptomCode();
        }
        ClaimSopNode node = sopService.matchSymptom(ClaimTexts.empty(claim.getProductCategory()) + "\n"
                + ClaimTexts.empty(claim.getProductName()) + "\n" + ClaimTexts.empty(claim.getSymptom()));
        if (node != null) {
            claim.setSymptomCode(node.getNodeCode());
            return node.getNodeCode();
        }
        return "";
    }

    private void fillCategory(ClaimCase claim) {
        if (!ClaimTexts.blank(claim.getProductCategory()) || ClaimTexts.blank(claim.getProductName())) {
            return;
        }
        String name = claim.getProductName();
        if (name.contains("蔬") || name.contains("瓜") || name.contains("茄") || name.contains("菜")) {
            claim.setProductCategory("蔬菜");
        } else if (name.contains("牛")) {
            claim.setProductCategory("牛肉");
        } else if (name.contains("羊")) {
            claim.setProductCategory("羊肉");
        }
    }

    private String collectedLine(ClaimCase claim) {
        List<String> parts = new ArrayList<>();
        add(parts, "顾客", claim.getCustomerName());
        add(parts, "电话", claim.getCustomerPhone());
        add(parts, "门店", claim.getStoreName());
        add(parts, "产品", claim.getProductName());
        add(parts, "现象", claim.getSymptom());
        add(parts, "购买时间", claim.getPurchaseDate());
        add(parts, "诉求", claim.getCustomerDemand());
        add(parts, "客群", claim.getCustomerType());
        if (parts.isEmpty()) {
            return "";
        }
        return "已记录：" + String.join("，", parts) + "。\n";
    }

    private void add(List<String> parts, String label, String value) {
        if (!ClaimTexts.blank(value)) {
            parts.add(label + " " + value);
        }
    }

    private String dialogue(Long caseId) {
        List<ClaimMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, caseId)
                .orderByAsc(ClaimMessage::getId));
        StringBuilder text = new StringBuilder();
        for (ClaimMessage message : messages) {
            text.append("user".equals(message.getRoleName()) ? "用户：" : "机器人：")
                    .append(ClaimTexts.empty(message.getContent()))
                    .append("\n");
        }
        return text.toString();
    }

    private String snapshot(ClaimCase claim) {
        return "姓名" + ClaimTexts.empty(claim.getCustomerName())
                + " 电话" + ClaimTexts.empty(claim.getCustomerPhone())
                + " 地址" + ClaimTexts.empty(claim.getCustomerAddress())
                + " 会员" + ClaimTexts.empty(claim.getMemberNo())
                + " 客群" + ClaimTexts.empty(claim.getCustomerType())
                + " 消费" + ClaimTexts.empty(claim.getConsumeSummary())
                + " 分类" + ClaimTexts.empty(claim.getProductCategory())
                + " 产品" + ClaimTexts.empty(claim.getProductName())
                + " 规格" + ClaimTexts.empty(claim.getProductSpec())
                + " 门店" + ClaimTexts.empty(claim.getStoreName())
                + " 购买日" + ClaimTexts.empty(claim.getPurchaseDate())
                + " 订单" + ClaimTexts.empty(claim.getOrderNo())
                + " 物流" + ClaimTexts.empty(claim.getLogisticsNo()) + " " + ClaimTexts.empty(claim.getLogisticsSummary())
                + " 现象" + ClaimTexts.empty(claim.getSymptom())
                + " 烹饪" + ClaimTexts.empty(claim.getCookingMethod())
                + " 诉求" + ClaimTexts.empty(claim.getCustomerDemand());
    }

    private void saveMessage(Long caseId, String role, String type, String content) {
        ClaimMessage message = new ClaimMessage();
        message.setCaseId(caseId);
        message.setRoleName(role);
        message.setMsgType(type);
        message.setContent(content);
        messageMapper.insert(message);
    }

    private String nameOf(String text) {
        return ClaimTexts.customerNameOf(text);
    }

    private String storeOf(String text) {
        return ClaimTexts.storeNameOf(text);
    }

    private String productOf(String text) {
        return ClaimTexts.productNameOf(text);
    }

    private String symptomOf(String text) {
        List<String> hits = new ArrayList<>();
        if (text.contains("羊") && (text.contains("太硬") || text.contains("很硬") || text.contains("硬了"))) {
            hits.add("羊肉太硬");
        } else if (text.contains("牛") && (text.contains("太硬") || text.contains("很硬") || text.contains("硬了"))) {
            hits.add("牛肉太硬");
        } else {
            addSymptom(hits, text, "太硬", "太硬", "很硬", "硬了");
        }
        addSymptom(hits, text, "肉老", "肉老", "口感老", "柴", "嚼不动", "咬不动");
        addSymptom(hits, text, "肉太肥", "太肥", "肥瘦", "油大");
        addSymptom(hits, text, "膻味", "膻", "腥");
        addSymptom(hits, text, "缺斤", "缺斤", "少两", "不够秤", "分量不够");
        addSymptom(hits, text, "异物", "异物", "头发", "塑料", "虫子");
        if (ClaimTexts.meatText(text) && !ClaimTexts.produceText(text)
                && ClaimTexts.spoilageText(text) && !text.contains("开化")) {
            hits.add("肉坏了");
        }
        addSymptom(hits, text, "颜色发黑", "发黑", "不新鲜", "变色");
        if (!ClaimTexts.meatText(text) || ClaimTexts.produceText(text)) {
            addSymptom(hits, text, "坏果", "坏果", "腐烂", "烂了");
        }
        addSymptom(hits, text, "开化", "开化");
        addSymptom(hits, text, "发货地不满", "发货地", "不是新疆");
        addSymptom(hits, text, "未收到", "丢失", "没收到", "未送到");
        addSymptom(hits, text, "不香", "不香", "没味", "怪味");
        return String.join("、", hits);
    }

    private void addSymptom(List<String> hits, String text, String label, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                hits.add(label);
                return;
            }
        }
    }

    private String demandOf(String text) {
        if (text.contains("退款")) {
            return "退款";
        }
        if (text.contains("补发") || text.contains("重发")) {
            return "补发";
        }
        if (text.contains("换货")) {
            return "换货";
        }
        if (text.contains("索赔") || text.contains("赔偿")) {
            return "索赔";
        }
        return "";
    }

    private String cookingOf(String text) {
        for (String method : List.of("红烧", "清炖", "火锅", "水煮", "煎", "烤", "蒸", "微波炉", "炖")) {
            if (text.contains(method)) {
                return method;
            }
        }
        return "";
    }

    private String consumeOf(String text) {
        if (text.contains("没买过") || text.contains("没有买过")) {
            return "没买过同款";
        }
        if (text.contains("经常买") || text.contains("一直买")) {
            return "经常买";
        }
        if (text.contains("第一次")) {
            return "第一次";
        }
        int at = text.indexOf("买过");
        if (at < 0) {
            return "";
        }
        int end = Math.min(text.length(), at + 8);
        return text.substring(at, end);
    }

    private String logisticsOf(String text) {
        List<String> hits = new ArrayList<>();
        for (String word : List.of("开化", "破损", "超时", "丢件", "未送达")) {
            if (text.contains(word)) {
                hits.add(word);
            }
        }
        Matcher waybill = Pattern.compile("SF\\d{8,}").matcher(text);
        if (waybill.find()) {
            hits.add(waybill.group());
        }
        return String.join("、", hits);
    }

    private String customerType(String text) {
        if (text.contains("老客") || text.contains("老顾客") || text.contains("老会员")) {
            return "老客";
        }
        if (text.contains("新客") || text.contains("新顾客") || text.contains("第一次买")) {
            return "新客";
        }
        return "";
    }

    private void setPhone(ClaimCase claim, String value) {
        setIfBlank(claim::getCustomerPhone, claim::setCustomerPhone, ClaimTexts.clip(value, 64));
    }

    private String appendFileErrors(String reply, List<ClaimFile> files) {
        if (files == null || files.isEmpty()) {
            return reply;
        }
        String errors = files.stream()
                .map(ClaimFile::error)
                .filter(error -> !ClaimTexts.blank(error))
                .distinct()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return errors.isBlank() ? reply : errors + "\n" + reply;
    }

    private void setIfBlank(Getter getter, Setter setter, String value) {
        if (ClaimTexts.blank(getter.get()) && !ClaimTexts.blank(value) && !"未知".equals(value)) {
            setter.set(ClaimTexts.clip(value, 500));
        }
    }

    private String json(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private JsonNode readJson(String raw) {
        if (ClaimTexts.blank(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private record Scored(ClaimHistoryCase row, int score) {
    }

    @FunctionalInterface
    private interface Getter {
        String get();
    }

    @FunctionalInterface
    private interface Setter {
        void set(String value);
    }

}
