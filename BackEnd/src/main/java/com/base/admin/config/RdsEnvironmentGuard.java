package com.base.admin.config;

import com.base.admin.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 测试与生产只允许连接 ECS 47.99.95.38 上的 MySQL（本机 127.0.0.1，或该公网地址）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RdsEnvironmentGuard implements ApplicationRunner {

    private final Environment environment;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @Override
    public void run(ApplicationArguments args) {
        String profiles = Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(profiles)) {
            profiles = String.join(",", environment.getDefaultProfiles());
        }
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        boolean allowed = !StringUtils.hasText(url)
                || url.contains("127.0.0.1")
                || url.contains("localhost")
                || url.contains(Constants.ECS_HOST);
        if (!allowed) {
            throw new IllegalStateException(
                    "当前 profile=[" + profiles + "] 只允许连接 ECS " + Constants.ECS_HOST
                            + " 上的数据库（127.0.0.1 或 " + Constants.ECS_HOST + "）。");
        }

        String hostHint = extractHostHint(jdbcUrl);
        log.info("RDS 环境校验通过 profile=[{}] jdbcHost={}", profiles, hostHint);
    }

    private static String extractHostHint(String url) {
        if (!StringUtils.hasText(url)) {
            return "(empty)";
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return url;
        }
        String rest = url.substring(start + 3);
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int q = rest.indexOf('?');
        if (q >= 0) {
            rest = rest.substring(0, q);
        }
        return rest;
    }
}
