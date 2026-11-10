package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
@RequiredArgsConstructor
public class DingTalkSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_dingtalk_app (
                  id             BIGINT       NOT NULL                COMMENT '固定为 1',
                  app_id         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '钉钉 AppId',
                  agent_id       VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '钉钉 AgentId',
                  client_id      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT 'Client ID / AppKey',
                  client_secret  VARCHAR(256) NOT NULL DEFAULT ''     COMMENT 'Client Secret',
                  corp_id        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '企业 CorpId',
                  enabled        TINYINT      NOT NULL DEFAULT 0      COMMENT '是否启用 1=启用 0=停用',
                  create_by      VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by      VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效 1=有效 0=已删除',
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉企业内部应用配置'
                """);
        ensureCorpIdColumn();
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM sys_dingtalk_app WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("""
                    INSERT IGNORE INTO sys_dingtalk_app (id, app_id, agent_id, client_id, client_secret, enabled, create_by, is_active)
                    VALUES (1, '', '', '', '', 0, 'system', 1)
                    """);
            log.info("已创建空的钉钉应用配置，请到「系统管理 → 钉钉应用配置」填写");
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS hr_user_dingtalk (
                  user_id            BIGINT      NOT NULL COMMENT '系统用户',
                  dingtalk_user_id   VARCHAR(64) NOT NULL COMMENT '企业userid',
                  dingtalk_union_id  VARCHAR(64) NOT NULL COMMENT 'unionId',
                  create_by          VARCHAR(50) DEFAULT '' COMMENT '创建者',
                  create_time        DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by          VARCHAR(50) DEFAULT '' COMMENT '更新者',
                  update_time        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active          TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
                  PRIMARY KEY (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钉钉身份'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user_online (
                  user_id     BIGINT       NOT NULL COMMENT 'User ID',
                  token_id    VARCHAR(64)  NOT NULL COMMENT 'JWT jti',
                  username    VARCHAR(50)  DEFAULT '' COMMENT 'Username',
                  ip          VARCHAR(128) DEFAULT '' COMMENT 'Login IP',
                  user_agent  VARCHAR(512) DEFAULT '' COMMENT 'User-Agent',
                  login_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Login time',
                  expire_time DATETIME     NOT NULL COMMENT 'Session expire time',
                  is_active   TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
                  PRIMARY KEY (user_id),
                  KEY idx_token_id (token_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User online session'
                """);
        jdbc.update("""
                INSERT INTO sys_role (role_name, role_key, sort_order, status, remark, is_active)
                SELECT '普通账号', 'hr_user', 2, 0, '钉钉扫码自动注册的默认角色', 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'hr_user' AND is_active = 1)
                """);
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES
                (142, '钉钉应用配置', 1, 5, 'dingtalk', 'system/dingtalk/index', 'C', 'system:dingtalk:list', 'LinkOutlined', 0, 0,
                 '保存钉钉企业内部应用凭证，供扫码登录使用', 1),
                (143, '钉钉应用编辑', 142, 1, '', '', 'F', 'system:dingtalk:edit', '#', 0, 0, '', 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name),
                  parent_id = VALUES(parent_id),
                  sort_order = VALUES(sort_order),
                  path = VALUES(path),
                  component = VALUES(component),
                  perms = VALUES(perms),
                  icon = VALUES(icon),
                  remark = VALUES(remark),
                  is_active = 1
                """);
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (142, 143)
                """);
        int retired = jdbc.update("""
                UPDATE sys_menu
                SET is_active = 0
                WHERE is_active = 1
                  AND menu_id NOT IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 140, 141, 142, 143, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224)
                  AND component NOT IN (
                    'system/user/index', 'system/role/index', 'system/menu/index',
                    'system/ai-provider/index', 'system/dingtalk/index',
                    'payroll/employee/index', 'payroll/base/index', 'payroll/social/index',
                    'payroll/dept/index', 'payroll/post/index', 'payroll/fee/index',
                    'payroll/attendance/index', 'payroll/bulletin/index', 'payroll/calendar/index', 'payroll/grade/index', 'payroll/performance/index', 'payroll/reward/index'
                  )
                  AND NOT (menu_type = 'F' AND parent_id IN (2, 3, 4, 140, 142, 201, 203, 205, 207, 209, 211, 213, 215, 217, 219, 221, 223))
                """);
        if (retired > 0) {
            log.info("已停用 {} 条非保留菜单", retired);
        }
        log.info("钉钉应用配置表与菜单已同步");
    }

    private void ensureCorpIdColumn() {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dingtalk_app' AND COLUMN_NAME = 'corp_id'
                """, Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute("""
                ALTER TABLE sys_dingtalk_app
                  ADD COLUMN corp_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '企业 CorpId' AFTER client_secret
                """);
        log.info("已为 sys_dingtalk_app 增加 corp_id 字段");
    }
}
