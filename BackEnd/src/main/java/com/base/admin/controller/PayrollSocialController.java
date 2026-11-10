package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.PaySocialRuleDTO;
import com.base.admin.domain.vo.PaySocialRuleVO;
import com.base.admin.service.PayrollSocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "社保缴纳规则", description = "上海、新疆城镇与农村通用社保比例")
@RestController
@RequestMapping("/payroll/social")
@RequiredArgsConstructor
public class PayrollSocialController {

    private final PayrollSocialService socialService;

    @Operation(summary = "社保缴纳规则列表")
    @GetMapping("/rules")
    @RequiresPermission("payroll:social:list")
    public Result<List<PaySocialRuleVO>> rules() {
        return Result.ok(socialService.list());
    }

    @Operation(summary = "保存社保缴纳规则")
    @PutMapping("/rules")
    @RequiresPermission("payroll:social:edit")
    @Log(title = "社保缴纳规则", businessType = 2)
    public Result<Void> save(@Valid @RequestBody PaySocialRuleDTO dto) {
        socialService.save(dto);
        return Result.ok();
    }
}
