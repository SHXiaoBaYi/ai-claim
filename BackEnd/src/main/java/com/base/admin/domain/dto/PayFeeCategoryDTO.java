package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "额外费用类目")
public class PayFeeCategoryDTO {

    @Schema(description = "主键，新增时为空")
    private Long id;

    @NotBlank(message = "请填写费用类目")
    @Size(max = 40, message = "费用类目不能超过40个字")
    @Schema(description = "费用类目", example = "新入职人员碳14费用")
    private String categoryName;

    @NotBlank(message = "请选择加钱或扣费")
    @Pattern(regexp = "add|deduct", message = "费用类型只能是加钱类或扣费类")
    @Schema(description = "add=加钱类 deduct=扣费类", example = "deduct")
    private String feeKind;
}
