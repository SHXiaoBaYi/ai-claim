package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉考勤或通报同步结果")
public class PayDingTalkPullVO {

    @Schema(description = "核算月份")
    private String period;

    @Schema(description = "写入条数")
    private int recordCount;

    @Schema(description = "说明")
    private String message;
}
