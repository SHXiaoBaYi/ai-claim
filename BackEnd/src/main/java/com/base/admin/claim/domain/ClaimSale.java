package com.base.admin.claim.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("claim_sale")
@Schema(description = "积累的销售记录")
public class ClaimSale extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "来源：ARCHIVE 档案 / HISTORY 历史案例")
    private String source;

    @Schema(description = "来源记录 ID")
    private Long sourceId;

    @Schema(description = "门店或平台")
    private String storeName;

    @Schema(description = "产品分类")
    private String productCategory;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "规格")
    private String productSpec;

    @Schema(description = "购买或投诉日期")
    private String purchaseDate;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "摘要")
    private String summary;
}
