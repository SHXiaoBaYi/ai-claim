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
@TableName("claim_message")
@Schema(description = "理赔对话消息")
public class ClaimMessage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "档案 ID")
    private Long caseId;

    @Schema(description = "user / bot")
    private String roleName;

    @Schema(description = "text / image / video / file")
    private String msgType;

    @Schema(description = "消息正文")
    private String content;
}
