package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayBulletinQueryDTO;
import com.base.admin.domain.dto.PayPeriodDTO;
import com.base.admin.domain.vo.PayBulletinVO;
import com.base.admin.domain.vo.PayDingTalkPullVO;
import com.base.admin.service.PayrollBulletinSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "行政通报", description = "核算工资前同步钉钉行政通报与奖惩")
@RestController
@RequestMapping("/payroll/bulletin")
@RequiredArgsConstructor
public class PayrollBulletinController {

    private final PayrollBulletinSyncService bulletinService;

    @Operation(summary = "行政通报分页")
    @PostMapping("/list")
    @RequiresPermission("payroll:bulletin:list")
    public Result<PageResult<PayBulletinVO>> page(@RequestBody(required = false) PayBulletinQueryDTO query) {
        return Result.ok(bulletinService.page(query == null ? new PayBulletinQueryDTO() : query));
    }

    @Operation(summary = "同步钉钉行政通报")
    @PostMapping("/sync")
    @RequiresPermission("payroll:bulletin:sync")
    @Log(title = "行政通报", businessType = 1)
    public Result<PayDingTalkPullVO> sync(@Valid @RequestBody PayPeriodDTO dto) {
        return Result.ok(bulletinService.sync(dto.getPeriod()));
    }
}
