package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改历史案例")
public class ClaimHistoryUpdateDTO {

    @NotNull
    @Schema(description = "案例 ID")
    private Long id;

    @Schema(description = "投诉日期")
    private String complaintDate;

    @Schema(description = "购买日期")
    private String purchaseDate;

    @Schema(description = "投诉类型")
    private String complaintType;

    @Schema(description = "购买门店")
    private String storeName;

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "顾客电话")
    private String customerPhone;

    @Schema(description = "顾客地址")
    private String customerAddress;

    @Schema(description = "投诉内容")
    private String complaintContent;

    @Schema(description = "产品分类")
    private String productCategory;

    @Schema(description = "产品分组")
    private String productGroup;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "投诉简述")
    private String symptomBrief;

    @Schema(description = "投诉现象大类")
    private String symptomCategory;

    @Schema(description = "理赔意见")
    private String claimOpinion;

    @Schema(description = "理赔产品")
    private String claimProduct;

    @Schema(description = "初步判定")
    private String judgment;

    @Schema(description = "第三方承担")
    private String thirdPartyAmount;

    @Schema(description = "理赔基金承担")
    private String fundAmount;

    @Schema(description = "追责承担")
    private String dutyAmount;

    @Schema(description = "运费")
    private String freight;

    @Schema(description = "结果")
    private String resultText;

    @Schema(description = "行政部处理结果")
    private String adminResult;

    @Schema(description = "归属部门")
    private String dutyDept;

    @Schema(description = "处理人")
    private String handlerName;
}
