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
@TableName("claim_history_case")
@Schema(description = "历史理赔案例")
public class ClaimHistoryCase extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "去重键")
    private String dedupeKey;

    @Schema(description = "来源年份")
    private String sourceYear;

    @Schema(description = "来源文件")
    private String sourceFile;

    @Schema(description = "投诉日期")
    private String complaintDate;

    @Schema(description = "购买日期")
    private String purchaseDate;

    @Schema(description = "投诉类型")
    private String complaintType;

    @Schema(description = "购买门店")
    private String storeName;

    @Schema(description = "是否回访投诉")
    private String revisit;

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

    @Schema(description = "产品分组，如羊肉、牛肉、蔬菜")
    private String productGroup;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "投诉简述")
    private String symptomBrief;

    @Schema(description = "投诉现象大类，如口感老柴、轻微开化")
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

    @Schema(description = "后台手工改过则重新导入时保留 1=是")
    private Integer manualEdited;
}
