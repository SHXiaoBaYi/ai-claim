package com.base.admin.claim.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("claim_case")
@Schema(description = "理赔档案")
public class ClaimCase extends BaseEntity {

    public static final String COLLECTING = "COLLECTING";
    public static final String RECOMMENDED = "RECOMMENDED";
    public static final String INTERRUPTED = "INTERRUPTED";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "钉钉会话 ID")
    private String conversationId;

    @Schema(description = "钉钉发送人 staffId")
    private String senderId;

    @Schema(description = "钉钉发送人昵称")
    private String senderName;

    @Schema(description = "COLLECTING / RECOMMENDED / INTERRUPTED")
    private String status;

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

    @Schema(description = "产品分组，如羊肉、牛肉、蔬菜")
    private String productGroup;

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

    @Schema(description = "投诉现象大类，如口感老柴、轻微开化")
    private String symptomCategory;

    @Schema(description = "命中的 SOP 现象编码")
    private String symptomCode;

    @Schema(description = "烹饪或解冻方式")
    private String cookingMethod;

    @Schema(description = "顾客诉求")
    private String customerDemand;

    @Schema(description = "当前等待补充的字段")
    private String pendingSlot;

    @Schema(description = "用户明确跳过的字段，逗号分隔")
    private String skippedSlots;

    @Schema(description = "命中的处理方式标题")
    private String sopTitle;

    @Schema(description = "理赔建议")
    private String recommendation;

    @Schema(description = "最近一次建议处理：ADOPTED 完全采纳 / REDO 不满意重出 / PARTIAL 部分采纳")
    private String adviceDecision;

    @JsonIgnore
    @Schema(hidden = true, description = "钉钉会话 webhook，只用于把重出的建议推回原会话")
    private String sessionWebhook;

    @Schema(description = "建议依据")
    private String recommendationBasis;

    @Schema(description = "参考历史案例 ID，逗号分隔")
    private String similarCaseIds;

    @Schema(description = "免登查看历史参照的令牌")
    private String referenceToken;

    @Schema(description = "免登页要展示的案例，JSON")
    private String referenceView;
}
