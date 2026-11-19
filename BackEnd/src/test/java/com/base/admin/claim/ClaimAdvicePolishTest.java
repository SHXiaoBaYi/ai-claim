package com.base.admin.claim;

import com.base.admin.claim.domain.ClaimSopNode;
import com.base.admin.claim.support.ClaimAdvicePolish;
import com.base.admin.claim.support.ClaimSopCatalog;
import com.base.admin.claim.support.ClaimTexts;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAdvicePolishTest {

    private static final Pattern NEXT_SECTION = Pattern.compile("\\n\\s*\\d+\\.\\s*");

    @Test
    void identifiableForeignDropsRetrievalAndRefundTalk() {
        String advice = ClaimTexts.advice(
                "头发异物，可直接判定。",
                "要照片，并安排取回样品核验。",
                "补发 1 件，或给您退款。",
                "您好，非常抱歉。我们可以给您退款或补发一件。");
        String out = ClaimAdvicePolish.apply("肉里有头发", advice);
        assertFalse(part(out, "行动步骤").contains("取回"));
        assertFalse(part(out, "行动步骤").contains("寄回"));
        assertTrue(part(out, "行动步骤").contains("核对物流时效和物流轨迹"));
        assertTrue(part(out, "行动步骤").contains("收货地址远近"));
        assertTrue(part(out, "具体方案").contains("退款"));
        assertFalse(part(out, "给顾客的参考话术").contains("退款"));
        assertTrue(part(out, "给顾客的参考话术").contains("补发"));
    }

    @Test
    void pepperAndOdorDoNotRetrieve() {
        String pepper = ClaimAdvicePolish.apply("吃出一粒花椒", ClaimTexts.advice(
                "花椒异物。", "安排寄回公司核验。", "补发 1 件。", "您好，我们先处理。"));
        assertFalse(part(pepper, "行动步骤").contains("寄回"));
        assertFalse(part(pepper, "行动步骤").contains("取回"));

        String smell = ClaimAdvicePolish.apply("这件羊肉有膻味", ClaimTexts.advice(
                "膻味。", "询问烹饪，并把样品寄回核验。", "补发 1 件。",
                "您好，抱歉给您带来异味，我们给您退款。"));
        assertFalse(part(smell, "行动步骤").contains("寄回"));
        assertFalse(part(smell, "给顾客的参考话术").contains("退款"));
        assertTrue(part(smell, "给顾客的参考话术").contains("补发"));
    }

    @Test
    void unknownForeignKeepsRetrieval() {
        String chip = ClaimAdvicePolish.apply("肉里有动物芯片", ClaimTexts.advice(
                "无法直接判定的异物。", "要清晰照片。", "核实后按标准处理。", "您好，我们先核实。"));
        assertTrue(part(chip, "行动步骤").contains("取回样品"));
        assertTrue(part(chip, "行动步骤").contains("收货地址远近"));

        String glass = ClaimAdvicePolish.apply("疑似玻璃碎片", ClaimTexts.advice(
                "疑似玻璃。", "已安排寄回核验。", "核实后再定赔偿。", "您好，我们先核验。"));
        assertTrue(part(glass, "行动步骤").contains("寄回"));
    }

    @Test
    void shortageStillRetrievesTheProduct() {
        String out = ClaimAdvicePolish.apply("缺斤少两", ClaimTexts.advice(
                "缺斤。", "取回原产品后补发。", "补发 1 件。", "您好，我们先核对重量。"));
        assertTrue(part(out, "行动步骤").contains("取回原产品"));
        assertTrue(part(out, "行动步骤").contains("物流轨迹"));
    }

    @Test
    void logisticsLineIsNotRepeated() {
        String advice = ClaimTexts.advice(
                "严重开化。",
                "核对物流时效和物流轨迹，并结合收货地址远近综合研判。",
                "补发 1 件。",
                "您好，我们给您补发。");
        String actions = part(ClaimAdvicePolish.apply("严重开化有异味", advice), "行动步骤");
        assertEquals(actions.indexOf("收货地址远近"), actions.lastIndexOf("收货地址远近"));
    }

    @Test
    void refundOnlyScriptFallsBackWithoutRefund() {
        String out = ClaimAdvicePolish.apply("包装漏气", ClaimTexts.advice(
                "漏气。", "核对订单。", "补发 1 件。", "请您申请全额退款。"));
        assertFalse(part(out, "给顾客的参考话术").contains("退款"));
        assertTrue(part(out, "给顾客的参考话术").contains("补发"));
    }

    @Test
    void catalogCarriesTheThreeRules() {
        assertTrue(content("product.foreign").contains("动物芯片"));
        assertTrue(content("product.foreign").contains("头发、花椒"));
        assertTrue(content("product.foreign.claim").contains("疑似玻璃"));
        assertTrue(content("product.smell").contains("不取回样品"));
        assertTrue(content("logistics").contains("收货地址远近"));
        assertTrue(content("logistics.severe").contains("物流轨迹"));
        assertFalse(content("product.foreign.no-claim").contains("退款"));
        assertFalse(content("product.color.logistics").contains("退款"));
    }

    private static String content(String code) {
        return ClaimSopCatalog.nodes().stream()
                .filter(node -> code.equals(node.getNodeCode()))
                .map(ClaimSopNode::getContent)
                .findFirst()
                .orElse("");
    }

    private static String part(String advice, String label) {
        String marker = label + "：";
        int start = advice.indexOf(marker);
        int body = start + marker.length();
        int end = advice.length();
        Matcher matcher = NEXT_SECTION.matcher(advice);
        if (matcher.find(body)) {
            end = matcher.start();
        }
        return advice.substring(body, end);
    }
}
