package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工额外费用查询")
public class PayEmployeeFeeQueryDTO {

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "费用类目ID")
    private Long categoryId;

    @Schema(description = "归属月份 yyyy-MM", example = "2026-10")
    private String feeMonth;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
