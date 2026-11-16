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
@TableName("claim_consumer_law")
@Schema(description = "消协法适用依据")
public class ClaimConsumerLawDoc extends BaseEntity {

    @TableId(type = IdType.INPUT)
    @Schema(description = "固定为 1")
    private Long id;

    @Schema(description = "消协法全文")
    private String content;
}
