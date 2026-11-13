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
@TableName("claim_advice_decision")
@Schema(description = "综合建议的人工处理记录")
public class ClaimAdviceDecision extends BaseEntity {

    public static final String ADOPTED = "ADOPTED";
    public static final String REDO = "REDO";
    public static final String PARTIAL = "PARTIAL";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "档案 ID")
    private Long caseId;

    @Schema(description = "ADOPTED 完全采纳 / REDO 不满意重出 / PARTIAL 部分采纳")
    private String decision;

    @Schema(description = "处理前的综合建议")
    private String originalAdvice;

    @Schema(description = "最终保留的综合建议，重出时为空")
    private String finalAdvice;

    @Schema(description = "处理时的现象大类")
    private String symptomCategory;

    @Schema(description = "处理时的产品分组")
    private String productGroup;

    @Schema(description = "处理人")
    private String deciderName;
}
