package com.base.admin.claim;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xby.claim")
public class ClaimProperties {

    /** 照片和视频目录 */
    private String dataDocs = "../data-docs";

    /** 三年客诉 Excel 目录 */
    private String historyDir = "../docs";

    /** 钉钉按钮打开的站点根地址，不要带末尾斜杠 */
    private String publicBaseUrl = "";
}
