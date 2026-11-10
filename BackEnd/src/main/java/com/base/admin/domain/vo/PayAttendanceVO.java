package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉考勤记录")
public class PayAttendanceVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "核算月份")
    private String period;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "工作日")
    private String workDate;

    @Schema(description = "OnDuty上班 OffDuty下班")
    private String checkType;

    @Schema(description = "打卡结果")
    private String timeResult;

    @Schema(description = "实际打卡时间")
    private String userCheckTime;

    @Schema(description = "应打卡时间")
    private String baseCheckTime;
}
