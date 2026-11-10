package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度考勤汇总")
public class PayAttendanceSummaryVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "算薪月份")
    private String period;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "迟到/早退次数")
    private Integer lateEarlyCount;

    @Schema(description = "事假1次数")
    private Integer personal1Count;

    @Schema(description = "事假2次数")
    private Integer personal2Count;

    @Schema(description = "旷工次数")
    private Integer absentCount;

    @Schema(description = "缺卡次数")
    private Integer missingCardCount;

    @Schema(description = "年假天数")
    private BigDecimal annualLeaveDays;

    @Schema(description = "婚/丧/产假天数")
    private BigDecimal specialLeaveDays;

    @Schema(description = "本月实休(调休)")
    private BigDecimal actualRestDays;

    @Schema(description = "剩余调休")
    private BigDecimal remainingCompDays;
}
