package com.base.admin.claim;

import com.base.admin.claim.support.ClaimHistoryExcel;
import com.base.admin.claim.support.ClaimHistoryExcel.SheetTable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimHistoryExcelTest {

    @Test
    void readsThreeYearWorkbooks() throws Exception {
        Path docs = Path.of("..", "docs");
        if (!Files.isDirectory(docs)) {
            docs = Path.of("docs");
        }
        assertTrue(count(docs, "2024") >= 60, "2024 客诉应能读出");
        assertTrue(count(docs, "2025") >= 400, "2025 客诉应能读出");
        int year2026 = ClaimHistoryExcel.read(first(docs, "2026")).stream()
                .filter(sheet -> "全年统计".equals(sheet.name()))
                .mapToInt(sheet -> sheet.rows().size())
                .sum();
        assertTrue(year2026 >= 150, "2026 全年统计应能读出，实际 " + year2026);
    }

    private int count(Path docs, String year) throws Exception {
        List<SheetTable> tables = ClaimHistoryExcel.read(first(docs, year));
        return tables.isEmpty() ? 0 : tables.get(0).rows().size();
    }

    private Path first(Path docs, String year) throws Exception {
        try (var files = Files.list(docs)) {
            return files.filter(path -> path.getFileName().toString().contains(year) && path.getFileName().toString().endsWith(".xlsx"))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
