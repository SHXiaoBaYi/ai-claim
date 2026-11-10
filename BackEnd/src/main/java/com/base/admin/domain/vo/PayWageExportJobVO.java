package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "工资明细导出任务进度")
public class PayWageExportJobVO {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "算薪月份")
    private String period;

    @Schema(description = "pending/running/done/failed")
    private String status;

    @Schema(description = "进度 0-100")
    private Integer percent;

    @Schema(description = "进度说明")
    private String message;

    @Schema(description = "完成后的文件名")
    private String fileName;
}
