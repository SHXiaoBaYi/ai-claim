package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayWorkCalendarSaveDTO;
import com.base.admin.domain.vo.PayWorkCalendarVO;
import com.base.admin.service.PayrollCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "计薪天数", description = "按国家放假安排生成，休息出勤计薪可改")
@RestController
@RequestMapping("/payroll/calendar")
@RequiredArgsConstructor
public class PayrollCalendarController {

    private final PayrollCalendarService calendarService;

    @Operation(summary = "某年计薪天数")
    @GetMapping
    @RequiresPermission("payroll:calendar:list")
    public Result<List<PayWorkCalendarVO>> list(@RequestParam(defaultValue = "2026") int year) {
        return Result.ok(calendarService.list(year));
    }

    @Operation(summary = "保存计薪天数")
    @PutMapping
    @RequiresPermission("payroll:calendar:edit")
    @Log(title = "计薪天数", businessType = 2)
    public Result<Void> save(@Valid @RequestBody PayWorkCalendarSaveDTO dto) {
        calendarService.save(dto);
        return Result.ok();
    }
}
