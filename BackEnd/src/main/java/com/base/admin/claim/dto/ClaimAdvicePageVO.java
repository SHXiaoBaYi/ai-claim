package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "综合建议处理页")
public class ClaimAdvicePageVO {

    @Schema(description = "顾客")
    private String customerName;

    @Schema(description = "产品")
    private String productName;

    @Schema(description = "现象")
    private String symptom;

    @Schema(description = "现象大类")
    private String symptomCategory;

    @Schema(description = "产品分组")
    private String productGroup;

    @Schema(description = "当前综合建议")
    private String recommendation;

    @Schema(description = "最近一次处理：ADOPTED / REDO / PARTIAL")
    private String decision;

    @Schema(description = "是否有历史参照页")
    private Boolean hasRefs;
}
