package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "额外费用类目")
public class PayFeeCategoryVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "费用类目")
    private String categoryName;

    @Schema(description = "hire=归属入职月且不可改")
    private String monthRule;

    @Schema(description = "add=加钱类 deduct=扣费类")
    private String feeKind;

    @Schema(description = "排序")
    private Integer sortOrder;
}
