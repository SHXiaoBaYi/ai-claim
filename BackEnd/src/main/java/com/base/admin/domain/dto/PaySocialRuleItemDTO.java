package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "社保险种比例")
public class PaySocialRuleItemDTO {

    @NotNull(message = "缺少险种")
    @Schema(description = "主键")
    private Long id;

    @NotNull(message = "请填写单位比例")
    @DecimalMin(value = "0", message = "单位比例不能为负数")
    @DecimalMax(value = "100", message = "单位比例不能超过100")
    @Schema(description = "单位缴费比例，百分数", example = "16")
    private BigDecimal employerRate;

    @NotNull(message = "请填写个人比例")
    @DecimalMin(value = "0", message = "个人比例不能为负数")
    @DecimalMax(value = "100", message = "个人比例不能超过100")
    @Schema(description = "个人缴费比例，百分数", example = "8")
    private BigDecimal personalRate;

    @DecimalMin(value = "0", message = "公积金基数下限不能为负数")
    @Schema(description = "本险种基数下限，空则用社保基数")
    private BigDecimal baseMin;

    @DecimalMin(value = "0", message = "公积金基数上限不能为负数")
    @Schema(description = "本险种基数上限，空则用社保基数")
    private BigDecimal baseMax;
}
