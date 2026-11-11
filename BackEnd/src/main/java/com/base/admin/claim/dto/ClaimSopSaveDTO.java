package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "保存 SOP 节点")
public class ClaimSopSaveDTO {

    @Schema(description = "主键，空则新增")
    private Long id;

    @NotBlank
    @Schema(description = "节点编码")
    private String nodeCode;

    @Schema(description = "父节点编码")
    private String parentCode;

    @NotBlank
    @Schema(description = "CATEGORY / SYMPTOM / ACTION")
    private String nodeType;

    @NotBlank
    @Schema(description = "标题")
    private String title;

    @Schema(description = "规则原文")
    private String content;

    @Schema(description = "关键词，逗号分隔")
    private String keywords;

    @Schema(description = "排序")
    private Integer sortOrder;
}
