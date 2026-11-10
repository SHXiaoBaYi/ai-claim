package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录日志、操作日志实体带逻辑删除字段，旧库缺列时登录插入会失败。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class LogSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        ensureIsActive("sys_login_log");
        ensureIsActive("sys_oper_log");
        ensureIsActive("sys_user_online");
    }

    private void ensureIsActive(String table) {
        Integer tableExists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, Integer.class, table);
        if (tableExists == null || tableExists == 0) {
            return;
        }
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'is_active'
                """, Integer.class, table);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table
                + " ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0删除'");
        log.info("已为 {} 增加 is_active 字段", table);
    }
}
