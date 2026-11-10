package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "社保缴纳规则")
public class PaySocialRuleDTO {

    @NotNull(message = "缺少社保规则")
    @Schema(description = "主键")
    private Long id;

    @NotNull(message = "请填写缴费基数下限")
    @DecimalMin(value = "0", message = "缴费基数下限不能为负数")
    @Schema(description = "缴费基数下限")
    private BigDecimal baseMin;

    @NotNull(message = "请填写缴费基数上限")
    @DecimalMin(value = "0", message = "缴费基数上限不能为负数")
    @Schema(description = "缴费基数上限")
    private BigDecimal baseMax;

    @Size(max = 500, message = "说明不能超过500字")
    @Schema(description = "口径说明")
    private String remark;

    @NotEmpty(message = "请填写险种比例")
    @Valid
    @Schema(description = "险种比例")
    private List<PaySocialRuleItemDTO> items;
}
