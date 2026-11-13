package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "免登页里的一条历史参照")
public class ClaimRefCaseVO {

    @Schema(description = "历史案例 ID")
    private Long id;

    @Schema(description = "等价度，0 到 1")
    private String score;

    @Schema(description = "ref 历史参照，similar 相似案例，special 特殊案例")
    private String kind;

    @Schema(description = "history 历史案例，archive 理赔档案")
    private String source;

    @Schema(description = "投诉日期")
    private String complaintDate;

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "现象原文")
    private String symptomBrief;

    @Schema(description = "理赔意见")
    private String claimOpinion;

    @Schema(description = "初步判定")
    private String judgment;

    @Schema(description = "结果")
    private String resultText;
}
