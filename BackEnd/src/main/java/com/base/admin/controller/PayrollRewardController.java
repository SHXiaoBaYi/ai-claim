package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayRewardDTO;
import com.base.admin.domain.dto.PayRewardQueryDTO;
import com.base.admin.domain.vo.PayRewardVO;
import com.base.admin.service.PayrollRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通报奖惩", description = "按月维护员工行政通报奖惩金额")
@RestController
@RequestMapping("/payroll/reward")
@RequiredArgsConstructor
public class PayrollRewardController {

    private final PayrollRewardService rewardService;

    @Operation(summary = "通报奖惩分页")
    @PostMapping("/list")
    @RequiresPermission("payroll:reward:list")
    public Result<PageResult<PayRewardVO>> page(@RequestBody(required = false) PayRewardQueryDTO query) {
        return Result.ok(rewardService.page(query == null ? new PayRewardQueryDTO() : query));
    }

    @Operation(summary = "保存通报奖惩")
    @PostMapping
    @RequiresPermission("payroll:reward:edit")
    @Log(title = "通报奖惩", businessType = 1)
    public Result<Void> save(@Valid @RequestBody PayRewardDTO dto) {
        rewardService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除通报奖惩")
    @DeleteMapping("/{id}")
    @RequiresPermission("payroll:reward:edit")
    @Log(title = "通报奖惩", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        rewardService.delete(id);
        return Result.ok();
    }
}
