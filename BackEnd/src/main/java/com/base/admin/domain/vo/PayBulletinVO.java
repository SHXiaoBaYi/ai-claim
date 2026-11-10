package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "行政通报")
public class PayBulletinVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "核算月份")
    private String period;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "分类")
    private String categoryName;

    @Schema(description = "发布人")
    private String author;

    @Schema(description = "口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过/通报")
    private String bulletinKind;

    @Schema(description = "正文")
    private String plainContent;

    @Schema(description = "发布时间")
    private String publishTime;

    @Schema(description = "接收范围")
    private String receiverText;
}
