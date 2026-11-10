package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "算薪部门")
public class PayDeptDTO {

    @Schema(description = "部门ID，新增时为空")
    private Long id;

    @NotBlank(message = "请填写部门名称")
    @Size(max = 50, message = "部门名称不能超过50字")
    @Schema(description = "部门名称", example = "人事部")
    private String deptName;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "0正常 1停用", example = "0")
    private Integer status;
}
