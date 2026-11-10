package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉考勤查询")
public class PayAttendanceQueryDTO {

    @Schema(description = "核算月份 yyyy-MM")
    private String period;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "打卡结果")
    private String timeResult;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
