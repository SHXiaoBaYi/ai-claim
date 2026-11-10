package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "核算月份")
public class PayPeriodDTO {

    @NotBlank(message = "请选择月份")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "月份格式应为 yyyy-MM")
    @Schema(description = "核算月份", example = "2026-10")
    private String period;
}
