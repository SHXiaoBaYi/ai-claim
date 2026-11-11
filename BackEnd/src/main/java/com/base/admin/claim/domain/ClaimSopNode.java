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
@TableName("claim_sop_node")
@Schema(description = "理赔 SOP 节点")
public class ClaimSopNode extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "节点编码")
    private String nodeCode;

    @Schema(description = "父节点编码，根节点为空")
    private String parentCode;

    @Schema(description = "CATEGORY / SYMPTOM / ACTION")
    private String nodeType;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "规则原文")
    private String content;

    @Schema(description = "现象关键词，逗号分隔")
    private String keywords;

    @Schema(description = "排序")
    private Integer sortOrder;
}
