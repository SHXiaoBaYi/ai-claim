package com.base.admin.claim;

import com.base.admin.claim.support.ClaimTexts;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimFactParseTest {

    @Test
    void splitsTheOpeningComplaintSentence() {
        String text = "顾客张三反馈上周在克西路店买的羊肉太硬了";
        LocalDate today = LocalDate.of(2026, 11, 11);
        assertEquals("张三", ClaimTexts.customerNameOf(text));
        assertEquals("克西路店", ClaimTexts.storeNameOf(text));
        assertEquals("羊肉", ClaimTexts.productNameOf(text));
        assertEquals("2026-11-02～2026-11-08", ClaimTexts.purchaseDateOf(text, today));
        assertFalse(ClaimTexts.isAbortPhrase(text));
    }

    @Test
    void recognizesAbortAndIgnoresGreeting() {
        assertTrue(ClaimTexts.isAbortPhrase("不投诉了"));
        assertTrue(ClaimTexts.isAbortPhrase("中断投诉"));
        assertFalse(ClaimTexts.isAbortPhrase("你好"));
        assertFalse(ClaimTexts.isAbortPhrase("新投诉"));
        assertEquals("", ClaimTexts.customerNameOf("你好"));
        assertEquals("", ClaimTexts.productNameOf("你好"));
    }

    @Test
    void keepsComplaintYearWithTheSourceFile() {
        assertEquals("2025-12-13", ClaimTexts.forceYear("2026-12-13", "2025"));
        assertEquals("2025-08-24", ClaimTexts.forceYear("2025-08-24", "2025"));
        assertEquals("2024-03-01", ClaimTexts.forceYear("2025-03-01", "2024"));
        assertEquals("", ClaimTexts.forceYear("", "2025"));
    }

    @Test
    void scoresBidirectionalEntailmentAndSkipsNoClaimWording() {
        assertEquals(0.93, ClaimTexts.bidirectionalEquivalence("entailment", 0.96, "蕴含", 0.93));
        assertEquals(0.85, ClaimTexts.bidirectionalEquivalence("entailment", 0.96, "entailment", 0.85));
        assertEquals(0.89, ClaimTexts.bidirectionalEquivalence("entailment", 0.97, "neutral", 0.4));
        assertEquals(0, ClaimTexts.bidirectionalEquivalence("entailment", 0.99, "contradiction", 0.9));
        assertEquals(0, ClaimTexts.bidirectionalEquivalence("neutral", 0.6, "neutral", 0.6));
        assertTrue(ClaimTexts.noClaimOutcome("不做处理"));
        assertTrue(ClaimTexts.noClaimOutcome("核实后无需理赔"));
        assertFalse(ClaimTexts.noClaimOutcome("追责处理"));
        assertEquals("产品-不香、不好吃", ClaimTexts.labeledPhenomenon("产品-不香、不好吃"));
        assertEquals("产品-不香、不好吃", ClaimTexts.labeledPhenomenon("顾客反馈产品-不香、不好吃，要求补发"));
        assertTrue(ClaimTexts.samePhenomenon("不香、不好吃", "产品-不香、不好吃"));
        assertFalse(ClaimTexts.samePhenomenon("不香", "产品-不香、不好吃"));
        assertTrue(ClaimTexts.containsPhenomenon("原话：产品-不香、不好吃", "产品-不香、不好吃"));
        assertFalse(ClaimTexts.containsPhenomenon("只说了不香", "产品-不香、不好吃"));
        assertEquals("正在对照历史案例\n██████████░░░░░░░░░░ 50%", ClaimTexts.progressBar("正在对照历史案例", 1, 2));
        assertTrue(ClaimTexts.progressBar("正在对照历史案例", 0, 4).endsWith("0%"));
        assertTrue(ClaimTexts.progressBar("正在对照历史案例", 4, 4).endsWith("100%"));
    }
}
