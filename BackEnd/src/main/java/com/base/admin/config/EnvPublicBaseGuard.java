package com.base.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 附件公网地址只允许当前 ECS，避免配图指向别的机器。
 */
@Component
public class EnvPublicBaseGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvPublicBaseGuard.class);
    private static final String ECS_HOST = "47.99.95.38";

    private final Environment environment;

    @Value("${xby.upload-public-base:}")
    private String uploadPublicBase;

    @Value("${dingtalk.h5-base-url:}")
    private String h5BaseUrl;

    public EnvPublicBaseGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = false;
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) {
                prod = true;
                break;
            }
        }
        assertThisServer("xby.upload-public-base", uploadPublicBase);
        assertThisServer("dingtalk.h5-base-url", h5BaseUrl);
        log.info("公网地址校验通过 profile={} upload-public-base={} h5-base-url={}",
                prod ? "prod" : "non-prod", uploadPublicBase, h5BaseUrl);
    }

    private static void assertThisServer(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (value.contains(ECS_HOST) || value.contains("nadaer.net")
                || value.contains("127.0.0.1") || value.contains("localhost")) {
            return;
        }
        throw new IllegalStateException(
                "配置项 " + name + " 只能指向 ECS " + ECS_HOST + "，当前=" + value);
    }
}
