package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部门分页查询")
public class PayDeptQueryDTO {

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "0正常 1停用")
    private Integer status;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
