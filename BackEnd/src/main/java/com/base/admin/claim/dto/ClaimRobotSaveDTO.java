package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "保存理赔机器人")
public class ClaimRobotSaveDTO {

    @Schema(description = "Client ID / AppKey")
    private String clientId;

    @Schema(description = "Client Secret，空表示不修改")
    private String clientSecret;

    @Schema(description = "机器人 RobotCode，用于下载图片和视频")
    private String robotCode;

    @Schema(description = "是否启用 1=启用")
    private Integer enabled;
}
