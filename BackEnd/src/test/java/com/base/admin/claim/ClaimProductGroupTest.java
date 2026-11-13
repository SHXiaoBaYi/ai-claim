package com.base.admin.claim;

import com.base.admin.claim.support.ClaimProductGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimProductGroupTest {

    @Test
    void groupsProductUnderTheSymptomCategory() {
        assertEquals("羊肉", ClaimProductGroup.of("精品羊肉", "", "羊肉-老、硬"));
        assertEquals("牛肉", ClaimProductGroup.of("牛腩", "牛肉", "牛肉-有异物"));
        assertEquals("禽类", ClaimProductGroup.of("肉鸽", "", "肉鸽-开化"));
        assertEquals("蔬菜", ClaimProductGroup.of("草莓", "", "草莓西红柿-变质"));
        assertEquals("熟食", ClaimProductGroup.of("牛肉饼", "", "牛肉饼-油太大"));
        assertEquals("熟食", ClaimProductGroup.of("", "", "羊蝎子火锅-涨袋"));
        assertEquals("羊肉", ClaimProductGroup.of("冷鲜羊肉", "", "物流-时效慢"));
        assertEquals("其他", ClaimProductGroup.of("", "", ""));
    }
}
