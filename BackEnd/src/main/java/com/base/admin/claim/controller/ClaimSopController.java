package com.base.admin.claim.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.claim.dto.ClaimSopNodeVO;
import com.base.admin.claim.dto.ClaimSopSaveDTO;
import com.base.admin.claim.service.ClaimSopService;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "理赔流程", description = "查看和编辑 SOP")
@RestController
@RequestMapping("/claim/sop")
@RequiredArgsConstructor
public class ClaimSopController {

    private final ClaimSopService sopService;

    @Operation(summary = "SOP 树")
    @GetMapping("/tree")
    @RequiresPermission("claim:sop:list")
    public Result<List<ClaimSopNodeVO>> tree() {
        return Result.ok(sopService.tree());
    }

    @Operation(summary = "新增 SOP 节点")
    @PostMapping
    @RequiresPermission("claim:sop:edit")
    @Log(title = "理赔流程", businessType = 1)
    public Result<Void> create(@Valid @RequestBody ClaimSopSaveDTO dto) {
        dto.setId(null);
        sopService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "修改 SOP 节点")
    @PutMapping
    @RequiresPermission("claim:sop:edit")
    @Log(title = "理赔流程", businessType = 2)
    public Result<Void> update(@Valid @RequestBody ClaimSopSaveDTO dto) {
        sopService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除 SOP 节点")
    @DeleteMapping("/{id}")
    @RequiresPermission("claim:sop:edit")
    @Log(title = "理赔流程", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        sopService.delete(id);
        return Result.ok();
    }
}
