package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "某月钉钉考勤与通报是否已同步")
public class PayMonthPullStatusVO {

    @Schema(description = "核算月份")
    private String period;

    @Schema(description = "考勤")
    private Item attendance;

    @Schema(description = "行政通报")
    private Item bulletin;

    @Data
    @Schema(description = "单项同步状态")
    public static class Item {

        @Schema(description = "条数，未同步时为空")
        private Integer recordCount;

        @Schema(description = "同步时间")
        private String syncedAt;
    }
}
