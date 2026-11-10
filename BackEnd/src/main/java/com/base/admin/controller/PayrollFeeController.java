package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PayEmployeeFeeDTO;
import com.base.admin.domain.dto.PayEmployeeFeeQueryDTO;
import com.base.admin.domain.dto.PayFeeCategoryDTO;
import com.base.admin.domain.vo.PayEmployeeFeeVO;
import com.base.admin.domain.vo.PayFeeCategoryVO;
import com.base.admin.domain.vo.PayFeeImageVO;
import com.base.admin.service.PayrollFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "员工额外费用", description = "核算工资时维护费用类目、金额、截图和备注")
@RestController
@RequestMapping("/payroll/fee")
@RequiredArgsConstructor
public class PayrollFeeController {

    private final PayrollFeeService feeService;

    @Operation(summary = "费用类目")
    @GetMapping("/categories")
    @RequiresPermission("payroll:fee:list")
    public Result<List<PayFeeCategoryVO>> categories() {
        return Result.ok(feeService.listCategories());
    }

    @Operation(summary = "保存费用类目")
    @PostMapping("/categories")
    @RequiresPermission("payroll:fee:edit")
    @Log(title = "额外费用类目", businessType = 1)
    public Result<Void> saveCategory(@Valid @RequestBody PayFeeCategoryDTO dto) {
        feeService.saveCategory(dto);
        return Result.ok();
    }

    @Operation(summary = "删除费用类目")
    @DeleteMapping("/categories/{id}")
    @RequiresPermission("payroll:fee:edit")
    @Log(title = "额外费用类目", businessType = 3)
    public Result<Void> deleteCategory(@PathVariable Long id) {
        feeService.deleteCategory(id);
        return Result.ok();
    }

    @Operation(summary = "额外费用分页")
    @PostMapping("/list")
    @RequiresPermission("payroll:fee:list")
    public Result<PageResult<PayEmployeeFeeVO>> page(@RequestBody(required = false) PayEmployeeFeeQueryDTO query) {
        return Result.ok(feeService.page(query == null ? new PayEmployeeFeeQueryDTO() : query));
    }

    @Operation(summary = "保存额外费用")
    @PostMapping
    @RequiresPermission("payroll:fee:edit")
    @Log(title = "员工额外费用", businessType = 1)
    public Result<Void> save(@Valid @RequestBody PayEmployeeFeeDTO dto) {
        feeService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除额外费用")
    @DeleteMapping("/{id}")
    @RequiresPermission("payroll:fee:edit")
    @Log(title = "员工额外费用", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        feeService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "上传费用截图")
    @PostMapping("/image")
    @RequiresPermission("payroll:fee:edit")
    public Result<PayFeeImageVO> image(@RequestParam("file") MultipartFile file) {
        return Result.ok(feeService.saveImage(file));
    }
}
