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
}
