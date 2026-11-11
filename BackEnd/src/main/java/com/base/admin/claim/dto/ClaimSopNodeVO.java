package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "SOP 节点")
public class ClaimSopNodeVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "节点编码")
    private String nodeCode;

    @Schema(description = "父节点编码")
    private String parentCode;

    @Schema(description = "CATEGORY / SYMPTOM / ACTION")
    private String nodeType;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "规则原文")
    private String content;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "子节点")
    private List<ClaimSopNodeVO> children = new ArrayList<>();
}
