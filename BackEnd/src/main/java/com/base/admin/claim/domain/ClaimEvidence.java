package com.base.admin.claim.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("claim_evidence")
@Schema(description = "理赔证据文件")
public class ClaimEvidence extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "档案 ID")
    private Long caseId;

    @Schema(description = "image / video / file")
    private String fileType;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "相对 data-docs 的路径")
    private String storagePath;

    @Schema(description = "字节数")
    private Long fileSize;
}
