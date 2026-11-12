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
import com.base.admin.claim.ClaimProperties;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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
    private final ClaimPhenomenonNli phenomenonNli;
    private final ClaimProperties claimProperties;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<ClaimAction> pendingAction = new ThreadLocal<>();

    public interface ClaimPace {
        void startWaiting();

        void stopWaiting();
    }

    public record ClaimAction(String title, String url) {
    }

    @lombok.Data
    public static class RefMark {
        private long id;
        private double score;
        private String kind;
    }

    public ClaimAction takeAction() {
        ClaimAction action = pendingAction.get();
        pendingAction.remove();
        return action;
    }

    public record ClaimFile(String kind, String fileName, byte[] bytes, String error) {
    }

    public record ClaimInbound(String conversationId, String conversationType, String senderId, String senderName,
                               String text, String msgType, List<ClaimFile> files) {
    }

    @Transactional
    public String handle(ClaimInbound inbound) {
        return handle(inbound, null);
    }

    @Transactional
    public String handle(ClaimInbound inbound, ClaimPace pace) {
        pendingAction.remove();
        if ("2".equals(inbound.conversationType())) {
            return "请在和理赔机器人的单聊里发送投诉，不要发在群里。";
        }
        String conversationId = ClaimTexts.empty(inbound.conversationId());
        if (conversationId.isBlank()) {
            return "没有拿到会话，请再发一次。";
        }
        synchronized (conversationId.intern()) {
            try {
                return handleLocked(inbound, conversationId, pace);
            } catch (RuntimeException ex) {
                pendingAction.remove();
                throw ex;
            }
        }
    }

    @Transactional
    public ClaimCase recommendAgain(Long caseId) {
        ClaimCase claim = caseMapper.selectById(caseId);
        if (claim == null) {
            return null;
        }
        String reply = finish(claim, null);
        pendingAction.remove();
        saveMessage(claim.getId(), "bot", "text", reply);
        return caseMapper.selectById(caseId);
    }

    private String handleLocked(ClaimInbound inbound, String conversationId, ClaimPace pace) {
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
            reply = finish(claim, pace);
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
        claim.setReferenceToken("");
        claim.setReferenceView("");
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
            rememberNewCustomer(claim);
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
                现象写成用户说的完整问题。顿号并列的都要保留，「产品-不香、不好吃」不要收成「不香」。
                没有分类前缀时写成「产品+问题」，例如羊肉太硬，不要只写太硬或肉太硬。
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
                && filled(claim, "customerType") && (newCustomer(claim) || filled(claim, "consumeSummary"))
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
        if (!newCustomer(claim) && !filled(claim, "consumeSummary")) return slot("consumeSummary", "请补充消费情况：大概买过几次，有没有买过同款。查不到就回复「没有」。");
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

    private String finish(ClaimCase claim, ClaimPace pace) {
        if (pace != null) {
            pace.startWaiting();
        }
        try {
            keepFullPhenomenon(claim);
            String blob = complaintBlob(claim);
            ClaimSopNode symptom = sopService.matchSymptom(blob);
            ClaimSopNode action = sopService.pickAction(symptom, claim);
            if (symptom != null) {
                claim.setSymptomCode(symptom.getNodeCode());
            }
            claim.setSopTitle(action == null ? "" : action.getTitle());
            HistoryMatches matches = matchHistory(claim, null);
            claim.setSimilarCaseIds(matches.ids());
            String evidence = evidence(symptom, action, matches);
            String suggestion = suggest(claim, action, matches, evidence);
            String page = saveReferences(claim, matches);
            if (pace != null && !ClaimTexts.blank(page)) {
                pendingAction.set(new ClaimAction("查看历史参照案例", page));
            }
            claim.setRecommendation(suggestion);
            claim.setRecommendationBasis(evidence);
            claim.setStatus(ClaimCase.RECOMMENDED);
            claim.setPendingSlot("");
            caseMapper.updateById(claim);
            return suggestion;
        } finally {
            if (pace != null) {
                pace.stopWaiting();
            }
        }
    }

    private String suggest(ClaimCase claim, ClaimSopNode action, HistoryMatches matches, String evidence) {
        if (!matches.judged()) {
            return "综合建议：历史对照没有完成，请人工按现象定方案。";
        }
        if (matches.actionable.isEmpty()) {
            String title = action == null ? "现象" : action.getTitle();
            return "综合建议：没有历史参照案例。按「" + title + "」处理。";
        }
        String decision = withoutNoHistoryClaim(synthesize(claim, sopSentence(action), evidence));
        if (!decision.startsWith("综合建议")) {
            decision = "综合建议：" + decision;
        }
        return decision;
    }

    /** 已经命中历史参照时，模型仍可能套「没有历史参照案例」这句，删掉。 */
    private String withoutNoHistoryClaim(String decision) {
        StringBuilder kept = new StringBuilder();
        for (String line : ClaimTexts.empty(decision).split("\\n")) {
            if (line.contains("没有历史参照案例")) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append('\n');
            }
            kept.append(line);
        }
        String text = kept.toString().trim();
        return text.isBlank() ? "综合建议：按历史参照案例的结案方式处理，并核对理赔 SOP。" : text;
    }

    /** 高于 0.85 的参照、相似案例，以及全部特殊案例，供免登页按等价度分 tab。 */
    private String saveReferences(ClaimCase claim, HistoryMatches matches) {
        List<RefMark> marks = new ArrayList<>();
        collectMarks(marks, matches.actionable, "ref", 0.85, false);
        collectMarks(marks, matches.similar, "similar", 0.85, false);
        collectMarks(marks, matches.special, "special", 0.8, true);
        if (marks.isEmpty()) {
            claim.setReferenceView("");
            return "";
        }
        marks.sort(Comparator.comparingDouble(RefMark::getScore).reversed());
        if (marks.size() > 40) {
            marks.subList(40, marks.size()).clear();
        }
        if (ClaimTexts.blank(claim.getReferenceToken())) {
            claim.setReferenceToken(UUID.randomUUID().toString().replace("-", ""));
        }
        try {
            claim.setReferenceView(objectMapper.writeValueAsString(marks));
        } catch (Exception ex) {
            log.warn("历史参照页数据写入失败: {}", ex.getMessage());
            claim.setReferenceView("");
            return "";
        }
        String base = ClaimTexts.empty(claimProperties.getPublicBaseUrl());
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            return "";
        }
        return base + "/claim/refs/" + claim.getReferenceToken();
    }

    private void collectMarks(List<RefMark> marks, List<Ranked> rows, String kind, double floor, boolean includeFloor) {
        for (Ranked item : rows) {
            boolean keep = includeFloor ? item.similarity >= floor : item.similarity > floor;
            if (!keep || item.row == null || item.row.getId() == null) {
                continue;
            }
            RefMark mark = new RefMark();
            mark.setId(item.row.getId());
            mark.setScore(item.similarity);
            mark.setKind(kind);
            marks.add(mark);
        }
    }

    private String synthesize(ClaimCase claim, String sop, String evidence) {
        String fallback = "综合建议：按历史参照案例的结案方式处理，并核对理赔 SOP。";
        if (!aiChatService.isEnabled()) {
            return fallback;
        }
        String system = """
                你是小巴依理赔助手。只写综合建议，以「综合建议：」开头。
                用三到五句讲清这次怎么处理：核实什么、补发还是退款、责任归谁。不要罗列案例，不要写依据标题。
                证据里已经有历史参照案例时，禁止写「没有历史参照案例」。
                特殊案例不能当作这次的处理动作。相似案例不能当主依据。
                货品不够、漏装不要写成缺斤，不要写退一赔三。缺斤查实才写退一赔三，异物索赔才写十倍且不足按 1000 元。
                牛羊肉坏了不要写成蔬菜坏果。用中文，不要输出 JSON。
                """;
        try {
            String answer = aiChatService.chat(system, "档案：\n" + snapshot(claim) + "\n\n" + evidence + "\n\nSOP：" + sop, null, 0.2);
            return ClaimTexts.blank(answer) ? fallback : answer.trim();
        } catch (Exception ex) {
            log.warn("理赔建议生成失败: {}", ex.getMessage());
            return fallback;
        }
    }

    private String sopSentence(ClaimSopNode action) {
        if (action == null) {
            return "没有命中流程分支，请人工按现象定方案。";
        }
        return "按「" + action.getTitle() + "」处理：" + ClaimTexts.empty(action.getContent());
    }

    private String evidence(ClaimSopNode symptom, ClaimSopNode action, HistoryMatches matches) {
        StringBuilder text = new StringBuilder();
        if (!matches.judged()) {
            text.append("历史参照案例：双向 NLI 没有完成，本次没有可参照的历史案例。\n");
        } else if (matches.actionable.isEmpty()) {
            text.append("历史参照案例：没有现象等价度达到 0.9 的可参照记录。\n");
        } else {
            text.append("历史参照案例（双向 NLI 等价度不低于 0.9）：\n");
            appendCases(text, matches.actionable);
        }
        if (!matches.special.isEmpty()) {
            text.append("特殊案例（现象等价，但无需理赔或不做处理）：\n");
            appendCases(text, matches.special);
        }
        if (!matches.similar.isEmpty()) {
            text.append("相似案例（等价度 0.8～0.9）：\n");
            appendCases(text, matches.similar);
        }
        if (symptom != null) {
            text.append("SOP 现象：").append(symptom.getTitle()).append("。").append(ClaimTexts.empty(symptom.getContent())).append("\n");
        }
        if (action != null && (symptom == null || !action.getNodeCode().equals(symptom.getNodeCode()))) {
            text.append("SOP 处理：").append(action.getTitle()).append("。").append(ClaimTexts.empty(action.getContent())).append("\n");
        }
        return text.toString().trim();
    }

    private void appendCases(StringBuilder text, List<Ranked> rows) {
        int index = 1;
        for (Ranked item : rows) {
            ClaimHistoryCase row = item.row;
            text.append(index++).append(". 等价度 ").append(String.format(java.util.Locale.ROOT, "%.2f", item.similarity)).append(" ")
                    .append(ClaimTexts.empty(row.getComplaintDate())).append(" ")
                    .append(ClaimTexts.empty(row.getCustomerName())).append(" ")
                    .append(ClaimTexts.empty(row.getSymptomBrief())).append(" / ")
                    .append(ClaimTexts.empty(row.getProductName()))
                    .append(" → ").append(clipOpinion(row))
                    .append("（").append(ClaimTexts.empty(row.getJudgment())).append("）\n");
        }
    }

    private String clipOpinion(ClaimHistoryCase item) {
        String opinion = ClaimTexts.empty(item.getClaimOpinion());
        if (opinion.isBlank()) {
            opinion = ClaimTexts.empty(item.getClaimProduct());
        }
        if (opinion.isBlank()) {
            opinion = ClaimTexts.empty(item.getResultText());
        }
        return ClaimTexts.clip(opinion.replace("\n", " "), 80);
    }

    private HistoryMatches matchHistory(ClaimCase claim, Consumer<String> progress) {
        List<ClaimHistoryCase> all = historyMapper.selectList(new LambdaQueryWrapper<ClaimHistoryCase>()
                .orderByDesc(ClaimHistoryCase::getId));
        String claimOriginal = complaintOriginal(claim);
        LinkedHashSet<String> phenomena = new LinkedHashSet<>();
        for (ClaimHistoryCase row : all) {
            if (categoryConflict(claim, row)) {
                continue;
            }
            String phenomenon = phenomenonOriginal(row);
            if (!phenomenon.isBlank()) {
                phenomena.add(phenomenon);
            }
        }
        Map<String, Double> exact = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>();
        for (String phenomenon : phenomena) {
            if (ClaimTexts.samePhenomenon(claim.getSymptom(), phenomenon)
                    || ClaimTexts.samePhenomenon(claimOriginal, phenomenon)
                    || ClaimTexts.containsPhenomenon(claim.getSymptom(), phenomenon)
                    || ClaimTexts.containsPhenomenon(claimOriginal, phenomenon)) {
                exact.put(phenomenon, 1.0);
            } else {
                pending.add(phenomenon);
            }
        }
        ClaimPhenomenonNli.NliJudgement judgement = phenomenonNli.judge(claimOriginal, pending, batch -> note(progress, batch));
        List<Ranked> actionable = new ArrayList<>();
        List<Ranked> special = new ArrayList<>();
        List<Ranked> similar = new ArrayList<>();
        boolean judged = pending.isEmpty() || judgement.completed() || !exact.isEmpty();
        if (judged) {
            for (ClaimHistoryCase row : all) {
                if (categoryConflict(claim, row)) {
                    continue;
                }
                String phenomenon = phenomenonOriginal(row);
                double similarity = exact.getOrDefault(phenomenon, judgement.scoreOf(phenomenon));
                if (similarity < 0.8) {
                    continue;
                }
                Ranked ranked = new Ranked(row, similarity);
                boolean noClaim = ClaimTexts.noClaimOutcome(row.getJudgment(), row.getResultText(), row.getClaimOpinion(), row.getAdminResult());
                if (noClaim) {
                    special.add(ranked);
                } else if (similarity >= 0.9) {
                    actionable.add(ranked);
                } else {
                    similar.add(ranked);
                }
            }
        }
        Comparator<Ranked> order = Comparator.comparingDouble((Ranked item) -> item.similarity).reversed();
        actionable.sort(order);
        special.sort(order);
        similar.sort(order);
        return new HistoryMatches(actionable, special, similar, judged);
    }

    /** 关键词抽取会把「产品-不香、不好吃」收成「不香」。原文里有完整现象时改回来。 */
    private void keepFullPhenomenon(ClaimCase claim) {
        List<ClaimMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, claim.getId())
                .orderByAsc(ClaimMessage::getId));
        String best = ClaimTexts.empty(claim.getSymptom());
        for (ClaimMessage message : messages) {
            if (!"user".equals(message.getRoleName())) {
                continue;
            }
            String labeled = ClaimTexts.labeledPhenomenon(message.getContent());
            if (labeled.length() > best.length()) {
                best = labeled;
            }
        }
        if (!best.equals(ClaimTexts.empty(claim.getSymptom()))) {
            claim.setSymptom(ClaimTexts.clip(best, 255));
        }
    }

    private void note(Consumer<String> progress, String text) {
        if (progress == null || ClaimTexts.blank(text)) {
            return;
        }
        try {
            progress.accept(text);
        } catch (Exception ex) {
            log.warn("理赔进度消息发送失败: {}", ex.getMessage());
        }
    }

    /** 新投诉原文：用户自己发来的话，不含机器人追问。 */
    private String complaintOriginal(ClaimCase claim) {
        List<ClaimMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, claim.getId())
                .orderByAsc(ClaimMessage::getId));
        StringBuilder text = new StringBuilder();
        for (ClaimMessage message : messages) {
            if (!"user".equals(message.getRoleName())) {
                continue;
            }
            String content = ClaimTexts.empty(message.getContent());
            if (content.isBlank() || content.startsWith("已发送证据")) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append("\n");
            }
            text.append(content);
        }
        String symptom = ClaimTexts.empty(claim.getSymptom());
        if (!symptom.isBlank()) {
            return "现象：" + symptom + (text.isEmpty() ? "" : "\n投诉原文：\n" + text);
        }
        return text.toString();
    }

    /** 历史案例的现象原文。没有简述时用投诉内容。 */
    private String phenomenonOriginal(ClaimHistoryCase row) {
        String brief = ClaimTexts.empty(row.getSymptomBrief());
        if (!brief.isBlank()) {
            return brief;
        }
        return ClaimTexts.clip(ClaimTexts.empty(row.getComplaintContent()), 180);
    }

    /** 肉类和蔬菜坏果不是同一现象，不送去 NLI。 */
    private boolean categoryConflict(ClaimCase claim, ClaimHistoryCase row) {
        String brief = ClaimTexts.empty(row.getSymptomBrief());
        String claimSide = ClaimTexts.empty(claim.getProductCategory()) + ClaimTexts.empty(claim.getProductName()) + ClaimTexts.empty(claim.getSymptom());
        String rowSide = ClaimTexts.empty(row.getProductCategory()) + ClaimTexts.empty(row.getProductName()) + brief;
        boolean claimMeat = ClaimTexts.meatText(claimSide) && !ClaimTexts.produceText(claimSide);
        boolean rowProduce = ClaimTexts.produceText(rowSide) || brief.contains("坏果");
        boolean rowMeat = ClaimTexts.meatText(rowSide);
        if (claimMeat && rowProduce && !rowMeat) {
            return true;
        }
        return ClaimTexts.produceText(claimSide) && !ClaimTexts.meatText(claimSide) && rowMeat && !ClaimTexts.produceText(rowSide);
    }

    private boolean newCustomer(ClaimCase claim) {
        return ClaimTexts.empty(claim.getCustomerType()).contains("新");
    }

    private void rememberNewCustomer(ClaimCase claim) {
        if (newCustomer(claim) && ClaimTexts.blank(claim.getConsumeSummary())) {
            claim.setConsumeSummary("新客");
        }
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
        String labeled = ClaimTexts.labeledPhenomenon(text);
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
        addSymptom(hits, text, "货品不够", "货品不够", "漏装", "少发", "少送", "漏货", "少配");
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
        addSymptom(hits, text, "不好吃", "不好吃");
        if (labeled.isBlank()) {
            return String.join("、", hits);
        }
        StringBuilder merged = new StringBuilder(labeled);
        for (String hit : hits) {
            if (!labeled.contains(hit)) {
                merged.append('、').append(hit);
            }
        }
        return merged.toString();
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

    private record Ranked(ClaimHistoryCase row, double similarity) {
    }

    private record HistoryMatches(List<Ranked> actionable, List<Ranked> special, List<Ranked> similar, boolean judged) {
        String ids() {
            StringBuilder text = new StringBuilder();
            appendIds(text, actionable);
            appendIds(text, special);
            appendIds(text, similar);
            return text.toString();
        }

        private static void appendIds(StringBuilder text, List<Ranked> rows) {
            for (Ranked item : rows) {
                String id = String.valueOf(item.row.getId());
                if (text.length() + id.length() + 1 > 1900) {
                    return;
                }
                if (!text.isEmpty()) {
                    text.append(',');
                }
                text.append(id);
            }
        }
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
