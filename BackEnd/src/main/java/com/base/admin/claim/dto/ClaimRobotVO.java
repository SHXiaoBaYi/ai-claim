package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "理赔机器人配置")
public class ClaimRobotVO {

    @Schema(description = "Client ID")
    private String clientId;

    @Schema(description = "脱敏后的 Client Secret")
    private String clientSecretMasked;

    @Schema(description = "是否已保存密钥")
    private Boolean hasClientSecret;

    @Schema(description = "RobotCode")
    private String robotCode;

    @Schema(description = "是否启用")
    private Integer enabled;

    @Schema(description = "Stream 是否已连接")
    private Boolean connected;
}
