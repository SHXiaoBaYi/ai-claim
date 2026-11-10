package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度绩效考核")
public class PayPerformanceVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "岗位")
    private String postName;

    @Schema(description = "职级")
    private String gradeName;

    @Schema(description = "考核月份")
    private String period;

    @Schema(description = "绩效考核系数")
    private BigDecimal coefficient;

    @Schema(description = "备注")
    private String remark;
}
