package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "算薪职级")
public class PayGradeDTO {

    @Schema(description = "职级ID，新增时为空")
    private Long id;

    @NotBlank(message = "请填写职级名称")
    @Size(max = 50, message = "职级名称不能超过50字")
    @Schema(description = "职级名称", example = "专员")
    private String gradeName;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "0正常 1停用", example = "0")
    private Integer status;
}
