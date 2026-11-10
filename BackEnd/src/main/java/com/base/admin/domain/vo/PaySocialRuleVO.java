package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "社保缴纳规则")
public class PaySocialRuleVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "地区")
    private String regionName;

    @Schema(description = "户口性质：城镇/农村")
    private String hukouType;

    @Schema(description = "缴费基数下限")
    private BigDecimal baseMin;

    @Schema(description = "缴费基数上限")
    private BigDecimal baseMax;

    @Schema(description = "口径说明")
    private String remark;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "险种比例")
    private List<PaySocialRuleItemVO> items = new ArrayList<>();
}
