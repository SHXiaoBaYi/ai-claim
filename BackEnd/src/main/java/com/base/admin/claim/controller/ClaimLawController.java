package com.base.admin.claim.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.claim.dto.ClaimLawVO;
import com.base.admin.claim.service.ClaimLawService;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "消协法", description = "查看和编辑消协法适用依据")
@RestController
@RequestMapping("/claim/law")
@RequiredArgsConstructor
public class ClaimLawController {

    private final ClaimLawService lawService;

    @Operation(summary = "消协法适用依据")
    @GetMapping
    @RequiresPermission("claim:law:list")
    public Result<ClaimLawVO> get() {
        ClaimLawVO vo = new ClaimLawVO();
        vo.setText(lawService.text());
        return Result.ok(vo);
    }

    @Operation(summary = "保存消协法适用依据")
    @PutMapping
    @RequiresPermission("claim:law:edit")
    @Log(title = "消协法", businessType = 2)
    public Result<Void> save(@Valid @RequestBody ClaimLawVO dto) {
        lawService.save(dto.getText());
        return Result.ok();
    }
}
