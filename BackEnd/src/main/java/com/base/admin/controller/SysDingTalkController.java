package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.DingTalkAppDTO;
import com.base.admin.domain.vo.DingTalkAppVO;
import com.base.admin.service.DingTalkAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "钉钉应用配置", description = "系统管理-钉钉企业内部应用凭证")
@RestController
@RequestMapping("/system/dingtalk")
@RequiredArgsConstructor
public class SysDingTalkController {

    private final DingTalkAppService dingTalkAppService;

    @Operation(summary = "查询钉钉应用配置")
    @GetMapping
    @RequiresPermission("system:dingtalk:list")
    public Result<DingTalkAppVO> get() {
        return Result.ok(dingTalkAppService.get());
    }

    @Operation(summary = "保存钉钉应用配置")
    @PutMapping
    @RequiresPermission("system:dingtalk:edit")
    @Log(title = "钉钉应用配置", businessType = 2)
    public Result<Void> save(@Valid @RequestBody DingTalkAppDTO dto) {
        dingTalkAppService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "用当前填写的凭证测试能否取到钉钉访问令牌")
    @PostMapping("/test")
    @RequiresPermission("system:dingtalk:edit")
    public Result<Void> test(@Valid @RequestBody DingTalkAppDTO dto) {
        DingTalkAppService.Credential credential = dingTalkAppService.resolveForTest(dto);
        dingTalkAppService.testConnection(credential.clientId(), credential.clientSecret());
        return Result.ok();
    }
}
