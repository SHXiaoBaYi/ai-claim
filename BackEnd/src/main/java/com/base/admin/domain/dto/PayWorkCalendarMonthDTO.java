package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "某月计薪天数")
public class PayWorkCalendarMonthDTO {

    @NotNull(message = "缺少月份")
    @Min(value = 1, message = "月份不正确")
    @Max(value = 12, message = "月份不正确")
    @Schema(description = "月份")
    private Integer month;

    @Size(max = 20, message = "法定假名称不能超过20个字")
    @Schema(description = "法定假名称")
    private String holidayName;

    @NotNull(message = "请填写法定假天数")
    @Min(value = 0, message = "天数不能为负")
    @Max(value = 31, message = "天数不能超过31")
    @Schema(description = "法定假天数")
    private Integer holidayDays;

    @NotNull(message = "请填写双休休息天数")
    @Min(0) @Max(31)
    @Schema(description = "双休休息天数")
    private Integer dualRest;

    @NotNull(message = "请填写双休出勤天数")
    @Min(0) @Max(31)
    @Schema(description = "双休出勤天数")
    private Integer dualWork;

    @NotNull(message = "请填写双休计薪天数")
    @Min(0) @Max(31)
    @Schema(description = "双休计薪天数")
    private Integer dualPay;

    @NotNull(message = "请填写单休休息天数")
    @Min(0) @Max(31)
    @Schema(description = "单休休息天数")
    private Integer singleRest;

    @NotNull(message = "请填写单休出勤天数")
    @Min(0) @Max(31)
    @Schema(description = "单休出勤天数")
    private Integer singleWork;

    @NotNull(message = "请填写单休计薪天数")
    @Min(0) @Max(31)
    @Schema(description = "单休计薪天数")
    private Integer singlePay;

    @NotNull(message = "请填写大小周休息天数")
    @Min(0) @Max(31)
    @Schema(description = "大小周休息天数")
    private Integer altRest;

    @NotNull(message = "请填写大小周出勤天数")
    @Min(0) @Max(31)
    @Schema(description = "大小周出勤天数")
    private Integer altWork;

    @NotNull(message = "请填写大小周计薪天数")
    @Min(0) @Max(31)
    @Schema(description = "大小周计薪天数")
    private Integer altPay;
}
