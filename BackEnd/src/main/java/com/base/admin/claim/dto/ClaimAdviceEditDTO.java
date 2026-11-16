package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部分修改综合建议")
public class ClaimAdviceEditDTO {

    @Schema(description = "事件性质")
    private String nature;

    @Schema(description = "行动步骤")
    private String actions;

    @Schema(description = "具体方案")
    private String plan;

    @Schema(description = "给顾客的参考话术")
    private String script;
}
