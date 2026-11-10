package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉通讯录同步结果")
public class PayDingTalkSyncVO {

    @Schema(description = "同步到的部门数")
    private int deptCount;

    @Schema(description = "同步到的员工数")
    private int employeeCount;

    @Schema(description = "新增员工数")
    private int created;

    @Schema(description = "更新员工数")
    private int updated;

    @Schema(description = "通讯录中已不在、标为离职的人数")
    private int resigned;
}
