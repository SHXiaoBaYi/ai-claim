package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部分修改综合建议")
public class ClaimAdviceEditDTO {

    @Schema(description = "事件定性")
    private String nature;

    @Schema(description = "行动要素")
    private String actions;

    @Schema(description = "具体方案")
    private String plan;
}
