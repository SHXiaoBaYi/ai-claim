package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayAttendanceQueryDTO;
import com.base.admin.domain.dto.PayAttendanceSummaryQueryDTO;
import com.base.admin.domain.dto.PayPeriodDTO;
import com.base.admin.domain.vo.PayAttendanceSummaryVO;
import com.base.admin.domain.vo.PayAttendanceVO;
import com.base.admin.domain.vo.PayDingTalkPullVO;
import com.base.admin.service.PayrollAttendanceSummaryService;
import com.base.admin.service.PayrollAttendanceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "钉钉考勤", description = "核算工资前同步钉钉打卡结果，并区分事假/旷工/缺卡/假期")
@RestController
@RequestMapping("/payroll/attendance")
@RequiredArgsConstructor
public class PayrollAttendanceController {

    private final PayrollAttendanceSyncService attendanceService;
    private final PayrollAttendanceSummaryService summaryService;

    @Operation(summary = "考勤打卡明细分页")
    @PostMapping("/list")
    @RequiresPermission("payroll:attendance:list")
    public Result<PageResult<PayAttendanceVO>> page(@RequestBody(required = false) PayAttendanceQueryDTO query) {
        return Result.ok(attendanceService.page(query == null ? new PayAttendanceQueryDTO() : query));
    }

    @Operation(summary = "考勤月度汇总分页")
    @PostMapping("/summary/list")
    @RequiresPermission("payroll:attendance:list")
    public Result<PageResult<PayAttendanceSummaryVO>> summary(
            @RequestBody(required = false) PayAttendanceSummaryQueryDTO query) {
        return Result.ok(summaryService.page(query == null ? new PayAttendanceSummaryQueryDTO() : query));
    }

    @Operation(summary = "按已同步打卡重算月度汇总")
    @PostMapping("/summary/rebuild")
    @RequiresPermission("payroll:attendance:sync")
    @Log(title = "考勤汇总", businessType = 1)
    public Result<Integer> rebuildSummary(@Valid @RequestBody PayPeriodDTO dto) {
        return Result.ok(summaryService.rebuild(dto.getPeriod()));
    }

    @Operation(summary = "同步钉钉考勤")
    @PostMapping("/sync")
    @RequiresPermission("payroll:attendance:sync")
    @Log(title = "钉钉考勤", businessType = 1)
    public Result<PayDingTalkPullVO> sync(@Valid @RequestBody PayPeriodDTO dto) {
        return Result.ok(attendanceService.sync(dto.getPeriod()));
    }
}
