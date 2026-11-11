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
@TableName("claim_customer")
@Schema(description = "积累的客户信息")
public class ClaimCustomer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "地址")
    private String customerAddress;

    @Schema(description = "会员号")
    private String memberNo;

    @Schema(description = "新客 / 老客")
    private String customerType;

    @Schema(description = "历史投诉次数")
    private Integer historyCount;

    @Schema(description = "理赔档案次数")
    private Integer archiveCount;

    @Schema(description = "销售记录笔数")
    private Integer saleCount;

    @Schema(description = "最近门店")
    private String lastStore;

    @Schema(description = "最近产品")
    private String lastProduct;

    @Schema(description = "最近出现日期")
    private String lastSeen;
}
