package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayEmployeeDTO;
import com.base.admin.domain.dto.PayEmployeeQueryDTO;
import com.base.admin.domain.dto.PayPeriodDTO;
import com.base.admin.domain.vo.PayDingTalkSyncVO;
import com.base.admin.domain.vo.PayEmployeeVO;
import com.base.admin.domain.vo.PayWageExportJobVO;
import com.base.admin.service.PayrollDingTalkSyncService;
import com.base.admin.service.PayrollEmployeeService;
import com.base.admin.service.PayrollWageExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "算薪员工", description = "员工档案")
@RestController
@RequestMapping("/payroll/employee")
@RequiredArgsConstructor
public class PayrollEmployeeController {

    private final PayrollEmployeeService employeeService;
    private final PayrollDingTalkSyncService dingTalkSyncService;
    private final PayrollWageExportService wageExportService;

    @Operation(summary = "分页查询员工")
    @PostMapping("/list")
    @RequiresPermission("payroll:employee:list")
    public Result<PageResult<PayEmployeeVO>> list(@RequestBody(required = false) PayEmployeeQueryDTO query) {
        return Result.ok(employeeService.list(query == null ? new PayEmployeeQueryDTO() : query));
    }

    @Operation(summary = "从钉钉企业通讯录同步员工")
    @PostMapping("/sync-dingtalk")
    @RequiresPermission("payroll:employee:edit")
    @Log(title = "算薪员工-钉钉同步", businessType = 2)
    public Result<PayDingTalkSyncVO> syncDingTalk() {
        return Result.ok(dingTalkSyncService.sync());
    }

    @Operation(summary = "保存员工")
    @PostMapping
    @RequiresPermission("payroll:employee:edit")
    @Log(title = "算薪员工", businessType = 1)
    public Result<Void> save(@Valid @RequestBody PayEmployeeDTO dto) {
        employeeService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除员工")
    @DeleteMapping("/{id}")
    @RequiresPermission("payroll:employee:edit")
    @Log(title = "算薪员工", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "开始生成工资明细 Excel")
    @PostMapping("/wage-export/start")
    @RequiresPermission("payroll:employee:list")
    @Log(title = "生成工资明细", businessType = 5)
    public Result<PayWageExportJobVO> startWageExport(@Valid @RequestBody PayPeriodDTO dto) {
        return Result.ok(wageExportService.start(dto.getPeriod()));
    }

    @Operation(summary = "查询工资明细生成进度")
    @GetMapping("/wage-export/{jobId}/progress")
    @RequiresPermission("payroll:employee:list")
    public Result<PayWageExportJobVO> wageExportProgress(@PathVariable String jobId) {
        return Result.ok(wageExportService.progress(jobId));
    }

    @Operation(summary = "下载已生成的工资明细")
    @GetMapping("/wage-export/{jobId}/download")
    @RequiresPermission("payroll:employee:list")
    public void downloadWageExport(@PathVariable String jobId, HttpServletResponse response) throws IOException {
        PayWageExportJobVO job = wageExportService.progress(jobId);
        String fileName = job.getFileName() == null ? "员工工资明细.xlsx" : job.getFileName();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        wageExportService.writeFile(jobId, response.getOutputStream());
        response.flushBuffer();
    }
}
