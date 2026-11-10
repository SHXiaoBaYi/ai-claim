package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayPerformanceDTO;
import com.base.admin.domain.dto.PayPerformanceQueryDTO;
import com.base.admin.domain.dto.PayPeriodDTO;
import com.base.admin.domain.vo.PayPerformanceVO;
import com.base.admin.service.PayrollPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "绩效考核", description = "按月维护员工绩效考核系数")
@RestController
@RequestMapping("/payroll/performance")
@RequiredArgsConstructor
public class PayrollPerformanceController {

    private final PayrollPerformanceService performanceService;

    @Operation(summary = "绩效考核分页")
    @PostMapping("/list")
    @RequiresPermission("payroll:performance:list")
    public Result<PageResult<PayPerformanceVO>> page(@RequestBody(required = false) PayPerformanceQueryDTO query) {
        return Result.ok(performanceService.page(query == null ? new PayPerformanceQueryDTO() : query));
    }

    @Operation(summary = "保存绩效考核")
    @PostMapping
    @RequiresPermission("payroll:performance:edit")
    @Log(title = "绩效考核", businessType = 1)
    public Result<Void> save(@Valid @RequestBody PayPerformanceDTO dto) {
        performanceService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除绩效考核")
    @DeleteMapping("/{id}")
    @RequiresPermission("payroll:performance:edit")
    @Log(title = "绩效考核", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        performanceService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "为当月在职员工生成考核行")
    @PostMapping("/generate")
    @RequiresPermission("payroll:performance:edit")
    @Log(title = "绩效考核生成", businessType = 1)
    public Result<Integer> generate(@Valid @RequestBody PayPeriodDTO dto) {
        return Result.ok(performanceService.generateMonth(dto.getPeriod()));
    }
}
