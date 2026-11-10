package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "某月计薪天数")
public class PayWorkCalendarVO {

    @Schema(description = "年份")
    private Integer year;

    @Schema(description = "月份")
    private Integer month;

    @Schema(description = "日历天数")
    private Integer calendarDays;

    @Schema(description = "法定假名称")
    private String holidayName;

    @Schema(description = "法定假天数")
    private Integer holidayDays;

    @Schema(description = "双休休息天数")
    private Integer dualRest;

    @Schema(description = "双休出勤天数")
    private Integer dualWork;

    @Schema(description = "双休计薪天数")
    private Integer dualPay;

    @Schema(description = "单休休息天数")
    private Integer singleRest;

    @Schema(description = "单休出勤天数")
    private Integer singleWork;

    @Schema(description = "单休计薪天数")
    private Integer singlePay;

    @Schema(description = "大小周休息天数")
    private Integer altRest;

    @Schema(description = "大小周出勤天数")
    private Integer altWork;

    @Schema(description = "大小周计薪天数")
    private Integer altPay;
}
