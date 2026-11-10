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
@Schema(description = "员工额外费用")
public class PayEmployeeFeeDTO {

    @Schema(description = "主键，新增时为空")
    private Long id;

    @NotNull(message = "请选择员工")
    @Schema(description = "员工ID")
    private Long employeeId;

    @NotNull(message = "请选择费用类目")
    @Schema(description = "费用类目ID")
    private Long categoryId;

    @NotBlank(message = "请选择归属月份")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "归属月份格式应为 yyyy-MM")
    @Schema(description = "归属月份", example = "2026-10")
    private String feeMonth;

    @NotNull(message = "请填写金额")
    @DecimalMin(value = "0.01", message = "金额至少为0.01")
    @DecimalMax(value = "9999999.99", message = "金额过大")
    @Schema(description = "金额", example = "120.00")
    private BigDecimal amount;

    @NotBlank(message = "请上传截图")
    @Size(max = 255, message = "截图路径过长")
    @Schema(description = "截图路径", example = "/uploads/payroll-fee/a.png")
    private String imageUrl;

    @Size(max = 500, message = "备注不能超过500个字")
    @Schema(description = "备注")
    private String remark;
}
