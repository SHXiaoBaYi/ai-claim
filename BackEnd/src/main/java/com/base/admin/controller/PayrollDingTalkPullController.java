package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.vo.PayMonthPullStatusVO;
import com.base.admin.service.PayrollDingTalkPullService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "钉钉同步状态", description = "核算工资前确认考勤和行政通报已同步")
@RestController
@RequestMapping("/payroll/dingtalk")
@RequiredArgsConstructor
public class PayrollDingTalkPullController {

    private final PayrollDingTalkPullService pullService;

    @Operation(summary = "某月考勤与通报同步状态")
    @GetMapping("/pull-status")
    @RequiresPermission({"payroll:attendance:list", "payroll:bulletin:list"})
    public Result<PayMonthPullStatusVO> status(@RequestParam String period) {
        return Result.ok(pullService.status(period));
    }
}
