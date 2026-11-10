package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度绩效考核")
public class PayPerformanceDTO {

    @Schema(description = "主键，新增时为空")
    private Long id;

    @NotNull(message = "请选择员工")
    @Schema(description = "员工ID")
    private Long employeeId;

    @NotBlank(message = "请选择考核月份")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "考核月份格式应为 yyyy-MM")
    @Schema(description = "考核月份", example = "2026-10")
    private String period;

    @NotNull(message = "请填写绩效考核系数")
    @DecimalMin(value = "0", message = "绩效考核系数不能为负")
    @DecimalMax(value = "9.9999", message = "绩效考核系数过大")
    @Schema(description = "绩效考核系数", example = "1.0000")
    private BigDecimal coefficient;

    @Size(max = 500, message = "备注不能超过500个字")
    @Schema(description = "备注")
    private String remark;
}
