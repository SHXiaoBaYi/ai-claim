package com.base.admin.claim;

import com.base.admin.claim.support.ClaimConsumerLaw;
import com.base.admin.claim.support.ClaimTexts;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimConsumerLawTest {

    @Test
    void readsXiaoxieDocAndKeepsPunitiveRouting() {
        Path docs = Path.of("..", "docs");
        if (!Files.isDirectory(docs)) {
            docs = Path.of("docs");
        }
        assertTrue(Files.isDirectory(docs), "docs 目录应存在");
        String text = ClaimConsumerLaw.load(docs);
        assertTrue(text.contains("第五十五条") || text.contains("退一赔三"), text);
        assertTrue(text.contains("148") || text.contains("退一赔十"), text);
        assertTrue(text.contains("保底"), text);
        assertTrue(ClaimConsumerLaw.specialClaim("顾客要求十倍赔偿"));
        assertTrue(ClaimConsumerLaw.specialClaim("要走消协索赔"));
        assertFalse(ClaimConsumerLaw.specialClaim("要求补发一件"));
        String advice = ClaimTexts.advice("食安异物", "要照片和凭证", "查实后退一赔十", "您好，我们先核实。");
        assertTrue(advice.contains("事件性质"));
        assertTrue(advice.contains("行动步骤"));
        assertTrue(advice.contains("具体方案"));
        assertTrue(advice.contains("给顾客的参考话术"));
    }
}
