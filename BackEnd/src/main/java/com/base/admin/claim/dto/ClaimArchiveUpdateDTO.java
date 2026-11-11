package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改理赔档案")
public class ClaimArchiveUpdateDTO {

    @NotNull
    @Schema(description = "档案 ID")
    private Long id;

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "顾客电话")
    private String customerPhone;

    @Schema(description = "顾客地址")
    private String customerAddress;

    @Schema(description = "会员号")
    private String memberNo;

    @Schema(description = "新客 / 老客")
    private String customerType;

    @Schema(description = "消费记录")
    private String consumeSummary;

    @Schema(description = "产品分类")
    private String productCategory;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "规格重量")
    private String productSpec;

    @Schema(description = "购买门店或平台")
    private String storeName;

    @Schema(description = "购买日期")
    private String purchaseDate;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "物流与开化情况")
    private String logisticsSummary;

    @Schema(description = "投诉现象")
    private String symptom;

    @Schema(description = "烹饪或解冻方式")
    private String cookingMethod;

    @Schema(description = "顾客诉求")
    private String customerDemand;

    @Schema(description = "理赔建议")
    private String recommendation;

    @Schema(description = "建议依据")
    private String recommendationBasis;
}
