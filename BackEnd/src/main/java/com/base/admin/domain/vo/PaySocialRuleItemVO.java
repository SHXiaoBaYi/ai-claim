package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "社保险种比例")
public class PaySocialRuleItemVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "险种编码")
    private String itemCode;

    @Schema(description = "险种名称")
    private String itemName;

    @Schema(description = "单位缴费比例，百分数")
    private BigDecimal employerRate;

    @Schema(description = "个人缴费比例，百分数")
    private BigDecimal personalRate;

    @Schema(description = "本险种基数下限，空则用社保基数")
    private BigDecimal baseMin;

    @Schema(description = "本险种基数上限，空则用社保基数")
    private BigDecimal baseMax;

    @Schema(description = "排序")
    private Integer sortOrder;
}
