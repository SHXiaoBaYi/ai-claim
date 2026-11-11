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
@TableName("claim_robot")
@Schema(description = "理赔机器人配置")
public class ClaimRobot extends BaseEntity {

    @TableId(type = IdType.INPUT)
    @Schema(description = "固定为 1")
    private Long id;

    @Schema(description = "Client ID / AppKey")
    private String clientId;

    @Schema(description = "Client Secret")
    private String clientSecret;

    @Schema(description = "机器人 RobotCode")
    private String robotCode;

    @Schema(description = "是否启用 1=启用 0=停用")
    private Integer enabled;
}
