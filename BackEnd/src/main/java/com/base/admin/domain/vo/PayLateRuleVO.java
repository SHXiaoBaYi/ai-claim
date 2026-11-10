package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "职级事假与通报奖惩单价")
public class PayLateRuleVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "职级ID")
    private Long gradeId;

    @Schema(description = "职级名称")
    private String gradeName;

    @Schema(description = "事假2起算门槛")
    private Integer allowCount;

    @Schema(description = "事假1整月定额")
    private BigDecimal personal1Amount;

    @Schema(description = "事假2每次扣款")
    private BigDecimal personal2Amount;

    @Schema(description = "口头表扬一次")
    private BigDecimal amtVerbalPraise;

    @Schema(description = "通报嘉奖一次")
    private BigDecimal amtCommendation;

    @Schema(description = "记功一次")
    private BigDecimal amtMerit;

    @Schema(description = "记大功一次")
    private BigDecimal amtMajorMerit;

    @Schema(description = "口头批评一次")
    private BigDecimal amtVerbalCritic;

    @Schema(description = "通报批评一次")
    private BigDecimal amtBulletinCritic;

    @Schema(description = "记过一次")
    private BigDecimal amtDemerit;

    @Schema(description = "记大过一次")
    private BigDecimal amtMajorDemerit;
}
