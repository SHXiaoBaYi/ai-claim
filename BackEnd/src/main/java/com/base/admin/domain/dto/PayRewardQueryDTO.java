package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通报奖惩查询")
public class PayRewardQueryDTO {

    @Schema(description = "算薪月份 yyyy-MM")
    private String period;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "奖/惩")
    private String rewardKind;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
