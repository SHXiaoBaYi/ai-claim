package com.base.admin.claim.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.dto.ClaimHistoryUpdateDTO;
import com.base.admin.claim.dto.ClaimImportResultVO;
import com.base.admin.claim.dto.ClaimPageQueryDTO;
import com.base.admin.claim.service.ClaimHistoryService;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "历史案例", description = "查看、编辑和导入三年客诉")
@RestController
@RequestMapping("/claim/history")
@RequiredArgsConstructor
public class ClaimHistoryController {

    private final ClaimHistoryService historyService;

    @Operation(summary = "分页查询历史案例")
    @PostMapping("/list")
    @RequiresPermission("claim:history:list")
    public Result<PageResult<ClaimHistoryCase>> list(@RequestBody ClaimPageQueryDTO query) {
        return Result.ok(historyService.page(query));
    }

    @Operation(summary = "历史案例详情")
    @GetMapping("/{id}")
    @RequiresPermission("claim:history:list")
    public Result<ClaimHistoryCase> detail(@PathVariable Long id) {
        return Result.ok(historyService.get(id));
    }

    @Operation(summary = "修改历史案例")
    @PutMapping
    @RequiresPermission("claim:history:edit")
    @Log(title = "历史案例", businessType = 2)
    public Result<Void> update(@Valid @RequestBody ClaimHistoryUpdateDTO dto) {
        historyService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "从 docs 目录重新导入三年客诉")
    @PostMapping("/import")
    @RequiresPermission("claim:history:edit")
    @Log(title = "历史案例", businessType = 1)
    public Result<ClaimImportResultVO> importDocs() {
        return Result.ok(historyService.importFromDocs());
    }
}
