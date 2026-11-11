package com.base.admin.claim.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.claim.ClaimDingTalkBridge;
import com.base.admin.claim.dto.ClaimRobotSaveDTO;
import com.base.admin.claim.dto.ClaimRobotVO;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "理赔机器人", description = "单独的理赔机器人凭证")
@RestController
@RequestMapping("/claim/robot")
@RequiredArgsConstructor
public class ClaimRobotController {

    private final ClaimDingTalkBridge dingTalkBridge;

    @Operation(summary = "查看理赔机器人配置")
    @GetMapping
    @RequiresPermission("claim:robot:list")
    public Result<ClaimRobotVO> get() {
        return Result.ok(dingTalkBridge.get());
    }

    @Operation(summary = "保存理赔机器人配置")
    @PutMapping
    @RequiresPermission("claim:robot:edit")
    @Log(title = "理赔机器人", businessType = 2)
    public Result<Void> save(@RequestBody ClaimRobotSaveDTO dto) {
        dingTalkBridge.save(dto);
        return Result.ok();
    }

    @Operation(summary = "测试理赔机器人能否连通钉钉")
    @PostMapping("/test")
    @RequiresPermission("claim:robot:edit")
    public Result<Void> test(@RequestBody ClaimRobotSaveDTO dto) {
        dingTalkBridge.test(dto);
        return Result.ok();
    }
}
