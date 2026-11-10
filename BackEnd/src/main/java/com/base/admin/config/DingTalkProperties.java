package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dingtalk")
public class DingTalkProperties {

    /** 库里没有启用配置时的备用开关 */
    private boolean enabled = false;

    private String clientId = "";

    private String clientSecret = "";

    /** 企业 CorpId；库表为空时作兜底 */
    private String corpId = "";
}
