package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度通报奖惩")
public class PayRewardVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "算薪月份")
    private String period;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "职级")
    private String gradeName;

    @Schema(description = "关联通报ID")
    private Long bulletinId;

    @Schema(description = "通报标题")
    private String bulletinTitle;

    @Schema(description = "奖/惩")
    private String rewardKind;

    @Schema(description = "事由")
    private String reason;

    @Schema(description = "次数")
    private Integer times;

    @Schema(description = "职级单价")
    private BigDecimal unitAmount;

    @Schema(description = "最终金额，奖为正惩为负")
    private BigDecimal amount;

    @Schema(description = "金额来源 bulletin/grade/manual")
    private String amountSource;

    @Schema(description = "备注")
    private String remark;
}
