package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "行政通报查询")
public class PayBulletinQueryDTO {

    @Schema(description = "核算月份 yyyy-MM")
    private String period;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "奖励/处分/通报")
    private String bulletinKind;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
