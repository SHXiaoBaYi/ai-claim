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
}
