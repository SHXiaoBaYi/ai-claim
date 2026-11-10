package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工额外费用")
public class PayEmployeeFeeVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "费用类目ID")
    private Long categoryId;

    @Schema(description = "费用类目")
    private String categoryName;

    @Schema(description = "add=加钱类 deduct=扣费类")
    private String feeKind;

    @Schema(description = "归属月份 yyyy-MM")
    private String feeMonth;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "截图路径")
    private String imageUrl;

    @Schema(description = "备注")
    private String remark;
}
