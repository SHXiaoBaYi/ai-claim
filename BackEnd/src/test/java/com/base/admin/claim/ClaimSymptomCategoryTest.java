package com.base.admin.claim;

import com.base.admin.claim.support.ClaimSymptomCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSymptomCategoryTest {

    @Test
    void groupsComplaintPhenomenonBeforeLookingUpCases() {
        assertEquals("口感老柴", ClaimSymptomCategory.classify("牛肉-老、硬、咬不动", "牛肉", "", "产品投诉", ""));
        assertEquals("变质不新鲜", ClaimSymptomCategory.classify("羊肉-变质", "羊肉", "", "产品投诉", ""));
        assertEquals("轻微开化", ClaimSymptomCategory.classify("冷鲜肉 开化", "冷鲜肉", "", "物流投诉", ""));
        assertEquals("严重开化", ClaimSymptomCategory.classify("冷鲜肉 开化变质", "羊肉", "", "物流投诉", ""));
        assertEquals("丢失超时", ClaimSymptomCategory.classify("物流-时效慢", "", "", "物流投诉", ""));
        assertEquals("服务不满", ClaimSymptomCategory.classify("服务-漏发短信、服务不满", "", "", "服务投诉", ""));
        assertEquals("漏装少发", ClaimSymptomCategory.classify("服务-漏装货", "", "", "服务投诉", ""));
        assertEquals("坏果", ClaimSymptomCategory.classify("草莓西红柿-变质", "草莓", "", "产品投诉", ""));
        assertEquals("异物", ClaimSymptomCategory.classify("牛肉-有异物", "牛肉", "", "产品投诉", ""));
        assertEquals("包装破损", ClaimSymptomCategory.classify("物流-羊肉包装破损", "羊肉", "", "物流投诉", ""));
        assertEquals("产品质量", ClaimSymptomCategory.classify("乳羔羊全羊-老版肉票", "乳羔羊", "", "产品投诉", ""));
        assertEquals("变质不新鲜", ClaimSymptomCategory.classify("肉坏了", "牛腩", "牛肉", "", ""));
        assertEquals("坏果", ClaimSymptomCategory.classify("蔬菜坏了", "有机菜", "蔬菜", "", ""));
    }

    @Test
    void widensOnlyInsideTheSameFamily() {
        assertEquals(java.util.List.of("口感老柴"), ClaimSymptomCategory.searchKeys("口感老柴"));
        assertTrue(ClaimSymptomCategory.searchKeys("物流服务").contains("轻微开化"));
        assertTrue(ClaimSymptomCategory.searchKeys("物流服务").contains("物流服务"));
        assertFalse(ClaimSymptomCategory.widenKeys("口感老柴").isEmpty());
        assertTrue(ClaimSymptomCategory.widenKeys("口感老柴").contains("膻腥异味"));
        assertFalse(ClaimSymptomCategory.widenKeys("口感老柴").contains("轻微开化"));
        assertTrue(ClaimSymptomCategory.widenKeys("物流服务").isEmpty());
        assertTrue(ClaimSymptomCategory.widenKeys("其他").isEmpty());
    }

    @Test
    void keepsAHandEditedCategoryUntilThePhenomenonChanges() {
        assertEquals("异物", ClaimSymptomCategory.choose("异物", "口感老柴", false, "口感老柴"));
        assertEquals("口感老柴", ClaimSymptomCategory.choose("口感老柴", "口感老柴", false, "口感老柴"));
        assertEquals("轻微开化", ClaimSymptomCategory.choose("口感老柴", "口感老柴", true, "轻微开化"));
        assertEquals("坏果", ClaimSymptomCategory.choose("", "", true, "坏果"));
    }
}
