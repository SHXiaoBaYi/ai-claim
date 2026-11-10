package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayDeptDTO;
import com.base.admin.domain.dto.PayDeptQueryDTO;
import com.base.admin.domain.dto.PayGradeDTO;
import com.base.admin.domain.dto.PayGradeQueryDTO;
import com.base.admin.domain.dto.PayLateRuleDTO;
import com.base.admin.domain.dto.PayPostDTO;
import com.base.admin.domain.dto.PayPostQueryDTO;
import com.base.admin.domain.vo.PayDeptVO;
import com.base.admin.domain.vo.PayGradeVO;
import com.base.admin.domain.vo.PayLateRuleVO;
import com.base.admin.domain.vo.PayPostVO;
import com.base.admin.service.PayrollBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "算薪基础信息", description = "部门、岗位、职级、迟到早退事假规则")
@RestController
@RequestMapping("/payroll/base")
@RequiredArgsConstructor
public class PayrollBaseController {

    private final PayrollBaseService baseService;

    @Operation(summary = "部门分页")
    @PostMapping("/depts/list")
    @RequiresPermission("payroll:base:list")
    public Result<PageResult<PayDeptVO>> deptPage(@RequestBody(required = false) PayDeptQueryDTO query) {
        return Result.ok(baseService.pageDepts(query == null ? new PayDeptQueryDTO() : query));
    }

    @Operation(summary = "部门列表")
    @GetMapping("/depts")
    @RequiresPermission({"payroll:base:list", "payroll:employee:list"})
    public Result<List<PayDeptVO>> depts() {
        return Result.ok(baseService.listDepts());
    }

    @Operation(summary = "保存部门")
    @PostMapping("/depts")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪部门", businessType = 1)
    public Result<Void> saveDept(@Valid @RequestBody PayDeptDTO dto) {
        baseService.saveDept(dto);
        return Result.ok();
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/depts/{id}")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪部门", businessType = 3)
    public Result<Void> deleteDept(@PathVariable Long id) {
        baseService.deleteDept(id);
        return Result.ok();
    }

    @Operation(summary = "岗位分页")
    @PostMapping("/posts/list")
    @RequiresPermission("payroll:base:list")
    public Result<PageResult<PayPostVO>> postPage(@RequestBody(required = false) PayPostQueryDTO query) {
        return Result.ok(baseService.pagePosts(query == null ? new PayPostQueryDTO() : query));
    }

    @Operation(summary = "岗位列表")
    @GetMapping("/posts")
    @RequiresPermission({"payroll:base:list", "payroll:employee:list"})
    public Result<List<PayPostVO>> posts() {
        return Result.ok(baseService.listPosts());
    }

    @Operation(summary = "保存岗位")
    @PostMapping("/posts")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪岗位", businessType = 1)
    public Result<Void> savePost(@Valid @RequestBody PayPostDTO dto) {
        baseService.savePost(dto);
        return Result.ok();
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/posts/{id}")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪岗位", businessType = 3)
    public Result<Void> deletePost(@PathVariable Long id) {
        baseService.deletePost(id);
        return Result.ok();
    }

    @Operation(summary = "职级分页")
    @PostMapping("/grades/list")
    @RequiresPermission("payroll:base:list")
    public Result<PageResult<PayGradeVO>> gradePage(@RequestBody(required = false) PayGradeQueryDTO query) {
        return Result.ok(baseService.pageGrades(query == null ? new PayGradeQueryDTO() : query));
    }

    @Operation(summary = "职级列表")
    @GetMapping("/grades")
    @RequiresPermission({"payroll:base:list", "payroll:employee:list"})
    public Result<List<PayGradeVO>> grades() {
        return Result.ok(baseService.listGrades());
    }

    @Operation(summary = "保存职级")
    @PostMapping("/grades")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪职级", businessType = 1)
    public Result<Void> saveGrade(@Valid @RequestBody PayGradeDTO dto) {
        baseService.saveGrade(dto);
        return Result.ok();
    }

    @Operation(summary = "删除职级")
    @DeleteMapping("/grades/{id}")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "算薪职级", businessType = 3)
    public Result<Void> deleteGrade(@PathVariable Long id) {
        baseService.deleteGrade(id);
        return Result.ok();
    }

    @Operation(summary = "职级事假规则列表")
    @GetMapping("/late-rules")
    @RequiresPermission("payroll:base:list")
    public Result<List<PayLateRuleVO>> lateRules() {
        return Result.ok(baseService.listLateRules());
    }

    @Operation(summary = "保存职级事假规则")
    @PutMapping("/late-rules")
    @RequiresPermission("payroll:base:edit")
    @Log(title = "事假规则", businessType = 2)
    public Result<Void> saveLateRule(@Valid @RequestBody PayLateRuleDTO dto) {
        baseService.saveLateRule(dto);
        return Result.ok();
    }
}
