package com.base.admin.claim.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimEvidence;
import com.base.admin.claim.dto.ClaimArchiveDetailVO;
import com.base.admin.claim.dto.ClaimArchiveUpdateDTO;
import com.base.admin.claim.dto.ClaimPageQueryDTO;
import com.base.admin.claim.service.ClaimArchiveService;
import com.base.admin.claim.service.ClaimConversationService;
import com.base.admin.claim.service.ClaimFileStorage;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@Tag(name = "理赔档案", description = "查看和修改钉钉对话落库的理赔档案")
@RestController
@RequestMapping("/claim/archive")
@RequiredArgsConstructor
public class ClaimArchiveController {

    private final ClaimArchiveService archiveService;
    private final ClaimConversationService conversationService;
    private final ClaimFileStorage fileStorage;

    @Operation(summary = "分页查询理赔档案")
    @PostMapping("/list")
    @RequiresPermission("claim:archive:list")
    public Result<PageResult<ClaimCase>> list(@RequestBody ClaimPageQueryDTO query) {
        return Result.ok(archiveService.page(query));
    }

    @Operation(summary = "理赔档案详情")
    @GetMapping("/{id}")
    @RequiresPermission("claim:archive:list")
    public Result<ClaimArchiveDetailVO> detail(@PathVariable Long id) {
        return Result.ok(archiveService.detail(id));
    }

    @Operation(summary = "修改已落库字段")
    @PutMapping
    @RequiresPermission("claim:archive:edit")
    @Log(title = "理赔档案", businessType = 2)
    public Result<Void> update(@Valid @RequestBody ClaimArchiveUpdateDTO dto) {
        archiveService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "按当前档案重新生成理赔建议")
    @PostMapping("/{id}/recommend")
    @RequiresPermission("claim:archive:edit")
    public Result<ClaimCase> recommend(@PathVariable Long id) {
        ClaimCase claim = conversationService.recommendAgain(id);
        if (claim == null) {
            throw new BusinessException("理赔档案不存在");
        }
        return Result.ok(claim);
    }

    @Operation(summary = "下载证据文件")
    @GetMapping("/evidence/{id}")
    @RequiresPermission("claim:archive:list")
    public ResponseEntity<Resource> evidence(@PathVariable Long id) {
        ClaimEvidence evidence = archiveService.evidence(id);
        Path path = fileStorage.resolve(evidence.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException("证据文件不在服务器上");
        }
        String name = evidence.getFileName() == null ? "evidence" : evidence.getFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"")
                .contentType(mediaType(evidence.getFileType()))
                .body(new FileSystemResource(path));
    }

    private MediaType mediaType(String fileType) {
        if ("video".equals(fileType)) {
            return MediaType.parseMediaType("video/mp4");
        }
        if ("image".equals(fileType)) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
