package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度通报奖惩")
public class PayRewardDTO {

    @Schema(description = "主键，新增时为空")
    private Long id;

    @NotBlank(message = "请选择算薪月份")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "月份格式应为 yyyy-MM")
    @Schema(description = "算薪月份", example = "2026-10")
    private String period;

    @NotNull(message = "请选择员工")
    @Schema(description = "员工ID")
    private Long employeeId;

    @NotBlank(message = "请选择奖惩类型")
    @Schema(description = "口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过", example = "通报嘉奖")
    private String rewardKind;

    @Size(max = 500, message = "事由不能超过500字")
    @Schema(description = "事由")
    private String reason;

    @NotNull(message = "请填写次数")
    @Min(value = 1, message = "次数至少为1")
    @Max(value = 99, message = "次数过大")
    @Schema(description = "次数", example = "1")
    private Integer times;

    @NotNull(message = "请填写金额")
    @Schema(description = "最终金额绝对值，保存时奖为正惩为负", example = "200")
    @DecimalMin(value = "0", message = "金额不能为负")
    private BigDecimal amount;

    @Size(max = 500, message = "备注不能超过500字")
    @Schema(description = "备注")
    private String remark;
}
