package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "算薪员工查询")
public class PayEmployeeQueryDTO {

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "岗位ID")
    private Long postId;

    @Schema(description = "职级ID")
    private Long gradeId;

    @Schema(description = "1在职 0离职")
    private Integer jobStatus;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
