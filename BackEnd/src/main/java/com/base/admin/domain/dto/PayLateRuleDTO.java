package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "职级事假与通报奖惩单价")
public class PayLateRuleDTO {

    @NotNull(message = "缺少职级规则")
    @Schema(description = "主键")
    private Long id;

    @NotNull(message = "请填写允许次数")
    @Min(0) @Max(31)
    @Schema(description = "事假2起算门槛")
    private Integer allowCount;

    @NotNull @DecimalMin("0")
    @Schema(description = "事假1整月定额")
    private BigDecimal personal1Amount;

    @NotNull @DecimalMin("0")
    @Schema(description = "事假2每次扣款")
    private BigDecimal personal2Amount;

    @NotNull @DecimalMin("0")
    @Schema(description = "口头表扬一次")
    private BigDecimal amtVerbalPraise;

    @NotNull @DecimalMin("0")
    @Schema(description = "通报嘉奖一次")
    private BigDecimal amtCommendation;

    @NotNull @DecimalMin("0")
    @Schema(description = "记功一次")
    private BigDecimal amtMerit;

    @NotNull @DecimalMin("0")
    @Schema(description = "记大功一次")
    private BigDecimal amtMajorMerit;

    @NotNull @DecimalMin("0")
    @Schema(description = "口头批评一次")
    private BigDecimal amtVerbalCritic;

    @NotNull @DecimalMin("0")
    @Schema(description = "通报批评一次")
    private BigDecimal amtBulletinCritic;

    @NotNull @DecimalMin("0")
    @Schema(description = "记过一次")
    private BigDecimal amtDemerit;

    @NotNull @DecimalMin("0")
    @Schema(description = "记大过一次")
    private BigDecimal amtMajorDemerit;
}
