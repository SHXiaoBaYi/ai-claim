package com.base.admin.claim.controller;

import com.base.admin.claim.dto.ClaimRefPageVO;
import com.base.admin.claim.service.ClaimReferenceService;
import com.base.admin.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "历史参照免登页", description = "钉钉按钮打开，不需要登录")
@RestController
@RequestMapping("/claim/refs")
@RequiredArgsConstructor
public class ClaimRefController {

    private final ClaimReferenceService referenceService;

    @Operation(summary = "按令牌查看历史参照，免登录")
    @GetMapping("/{token}")
    public Result<ClaimRefPageVO> open(@PathVariable String token) {
        return Result.ok(referenceService.open(token));
    }
}
