package com.base.admin.claim.controller;

import com.base.admin.claim.dto.ClaimAdviceEditDTO;
import com.base.admin.claim.dto.ClaimAdvicePageVO;
import com.base.admin.claim.service.ClaimAdviceService;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "综合建议处理", description = "钉钉卡片打开，不需要登录")
@RestController
@RequestMapping("/claim/advice")
@RequiredArgsConstructor
public class ClaimAdviceController {

    private final ClaimAdviceService adviceService;

    @Operation(summary = "查看当前综合建议，免登录")
    @GetMapping("/{token}")
    public Result<ClaimAdvicePageVO> open(@PathVariable String token) {
        return Result.ok(adviceService.open(token));
    }

    @Operation(summary = "完全采纳，免登录")
    @PostMapping("/{token}/adopt")
    public Result<ClaimAdvicePageVO> adopt(@PathVariable String token) {
        return Result.ok(adviceService.adopt(token));
    }

    @Operation(summary = "不满意并重新出建议，免登录")
    @PostMapping("/{token}/redo")
    public Result<ClaimAdvicePageVO> redo(@PathVariable String token) {
        ClaimAdvicePageVO page = adviceService.redo(token);
        adviceService.push(token);
        return Result.ok(page);
    }

    @Operation(summary = "部分修改后采纳，免登录")
    @PostMapping("/{token}/partial")
    public Result<ClaimAdvicePageVO> partial(@PathVariable String token, @RequestBody ClaimAdviceEditDTO dto) {
        return Result.ok(adviceService.partial(token, dto));
    }
}
