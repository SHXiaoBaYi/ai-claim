package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "绩效考核查询")
public class PayPerformanceQueryDTO {

    @Schema(description = "考核月份 yyyy-MM")
    private String period;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
