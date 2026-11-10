package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "保存一年计薪天数")
public class PayWorkCalendarSaveDTO {

    @NotNull(message = "请选择年份")
    @Min(value = 2000, message = "年份不正确")
    @Max(value = 2100, message = "年份不正确")
    @Schema(description = "年份")
    private Integer year;

    @Valid
    @NotEmpty(message = "没有可保存的月份")
    @Schema(description = "十二个月")
    private List<PayWorkCalendarMonthDTO> months;
}
