package com.base.admin.claim;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class ClaimSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_robot (
                  id             BIGINT       NOT NULL,
                  client_id      VARCHAR(128) NOT NULL DEFAULT '',
                  client_secret  VARCHAR(256) NOT NULL DEFAULT '',
                  robot_code     VARCHAR(128) NOT NULL DEFAULT '',
                  enabled        TINYINT      NOT NULL DEFAULT 0,
                  create_by      VARCHAR(50)  DEFAULT '',
                  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by      VARCHAR(50)  DEFAULT '',
                  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active      TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔机器人'
                """);
        Integer robotCount = jdbc.queryForObject("SELECT COUNT(1) FROM claim_robot WHERE id = 1", Integer.class);
        if (robotCount == null || robotCount == 0) {
            jdbc.update("""
                    INSERT INTO claim_robot (id, client_id, client_secret, robot_code, enabled, create_by, is_active)
                    VALUES (1, '', '', '', 0, 'system', 1)
                    """);
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_sop_node (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  node_code    VARCHAR(64)  NOT NULL,
                  parent_code  VARCHAR(64)  NOT NULL DEFAULT '',
                  node_type    VARCHAR(16)  NOT NULL,
                  title        VARCHAR(128) NOT NULL,
                  content      TEXT,
                  keywords     VARCHAR(255) NOT NULL DEFAULT '',
                  sort_order   INT          NOT NULL DEFAULT 0,
                  create_by    VARCHAR(50)  DEFAULT '',
                  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by    VARCHAR(50)  DEFAULT '',
                  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active    TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_claim_sop_code (node_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔SOP'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_case (
                  id                   BIGINT        NOT NULL AUTO_INCREMENT,
                  conversation_id      VARCHAR(128)  NOT NULL DEFAULT '',
                  sender_id            VARCHAR(64)   NOT NULL DEFAULT '',
                  sender_name          VARCHAR(64)   NOT NULL DEFAULT '',
                  status               VARCHAR(32)   NOT NULL DEFAULT 'COLLECTING',
                  customer_name        VARCHAR(500)  NOT NULL DEFAULT '',
                  customer_phone       VARCHAR(64)   NOT NULL DEFAULT '',
                  customer_address     VARCHAR(500)  NOT NULL DEFAULT '',
                  member_no            VARCHAR(128)  NOT NULL DEFAULT '',
                  customer_type        VARCHAR(32)   NOT NULL DEFAULT '',
                  consume_summary      VARCHAR(1000) NOT NULL DEFAULT '',
                  product_category     VARCHAR(128)  NOT NULL DEFAULT '',
                  product_name         VARCHAR(500)  NOT NULL DEFAULT '',
                  product_spec         VARCHAR(255)  NOT NULL DEFAULT '',
                  store_name           VARCHAR(255)  NOT NULL DEFAULT '',
                  purchase_date        VARCHAR(64)   NOT NULL DEFAULT '',
                  order_no             VARCHAR(128)  NOT NULL DEFAULT '',
                  logistics_no         VARCHAR(128)  NOT NULL DEFAULT '',
                  logistics_summary    VARCHAR(1000) NOT NULL DEFAULT '',
                  symptom              VARCHAR(500)  NOT NULL DEFAULT '',
                  symptom_code         VARCHAR(64)   NOT NULL DEFAULT '',
                  cooking_method       VARCHAR(500)  NOT NULL DEFAULT '',
                  customer_demand      VARCHAR(500)  NOT NULL DEFAULT '',
                  pending_slot         VARCHAR(32)   NOT NULL DEFAULT '',
                  skipped_slots        VARCHAR(500)  NOT NULL DEFAULT '',
                  sop_title            VARCHAR(128)  NOT NULL DEFAULT '',
                  recommendation       TEXT,
                  recommendation_basis TEXT,
                  similar_case_ids     VARCHAR(128)  NOT NULL DEFAULT '',
                  create_by            VARCHAR(50)   DEFAULT '',
                  create_time          DATETIME      DEFAULT CURRENT_TIMESTAMP,
                  update_by            VARCHAR(50)   DEFAULT '',
                  update_time          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active            TINYINT       NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  KEY idx_claim_case_conv (conversation_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔档案'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_message (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  case_id      BIGINT       NOT NULL,
                  role_name    VARCHAR(16)  NOT NULL,
                  msg_type     VARCHAR(32)  NOT NULL DEFAULT 'text',
                  content      TEXT,
                  create_by    VARCHAR(50)  DEFAULT '',
                  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by    VARCHAR(50)  DEFAULT '',
                  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active    TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  KEY idx_claim_message_case (case_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔对话'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_evidence (
                  id            BIGINT       NOT NULL AUTO_INCREMENT,
                  case_id       BIGINT       NOT NULL,
                  file_type     VARCHAR(16)  NOT NULL,
                  file_name     VARCHAR(255) NOT NULL,
                  storage_path  VARCHAR(500) NOT NULL,
                  file_size     BIGINT       NOT NULL DEFAULT 0,
                  create_by     VARCHAR(50)  DEFAULT '',
                  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
                  update_by     VARCHAR(50)  DEFAULT '',
                  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active     TINYINT      NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  KEY idx_claim_evidence_case (case_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔证据'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS claim_history_case (
                  id                 BIGINT        NOT NULL AUTO_INCREMENT,
                  dedupe_key         VARCHAR(64)   NOT NULL,
                  source_year        VARCHAR(8)    NOT NULL DEFAULT '',
                  source_file        VARCHAR(128)  NOT NULL DEFAULT '',
                  complaint_date     VARCHAR(32)   NOT NULL DEFAULT '',
                  purchase_date      VARCHAR(32)   NOT NULL DEFAULT '',
                  complaint_type     VARCHAR(32)   NOT NULL DEFAULT '',
                  store_name         VARCHAR(128)  NOT NULL DEFAULT '',
                  revisit            VARCHAR(16)   NOT NULL DEFAULT '',
                  customer_name      VARCHAR(64)   NOT NULL DEFAULT '',
                  customer_phone     VARCHAR(32)   NOT NULL DEFAULT '',
                  customer_address   VARCHAR(255)  NOT NULL DEFAULT '',
                  complaint_content  TEXT,
                  product_category   VARCHAR(64)   NOT NULL DEFAULT '',
                  product_name       VARCHAR(255)  NOT NULL DEFAULT '',
                  symptom_brief      VARCHAR(255)  NOT NULL DEFAULT '',
                  claim_opinion      TEXT,
                  claim_product      VARCHAR(500)  NOT NULL DEFAULT '',
                  judgment           VARCHAR(64)   NOT NULL DEFAULT '',
                  third_party_amount VARCHAR(32)   NOT NULL DEFAULT '',
                  fund_amount        VARCHAR(32)   NOT NULL DEFAULT '',
                  duty_amount        VARCHAR(32)   NOT NULL DEFAULT '',
                  freight            VARCHAR(32)   NOT NULL DEFAULT '',
                  result_text        VARCHAR(64)   NOT NULL DEFAULT '',
                  admin_result       TEXT,
                  duty_dept          VARCHAR(128)  NOT NULL DEFAULT '',
                  handler_name       VARCHAR(64)   NOT NULL DEFAULT '',
                  manual_edited      TINYINT       NOT NULL DEFAULT 0,
                  create_by          VARCHAR(50)   DEFAULT '',
                  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP,
                  update_by          VARCHAR(50)   DEFAULT '',
                  update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active          TINYINT       NOT NULL DEFAULT 1,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_claim_history_dedupe (dedupe_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史理赔案例'
                """);
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES
                (300, '理赔助手', 0, 2, 'claim', '', 'M', '', 'CustomerServiceOutlined', 0, 0, '钉钉理赔收集与档案', 1),
                (301, '理赔档案', 300, 1, 'archive', 'claim/archive/index', 'C', 'claim:archive:list', 'ProfileOutlined', 0, 0, '查看和修改对话落库的档案', 1),
                (302, '档案编辑', 301, 1, '', '', 'F', 'claim:archive:edit', '#', 0, 0, '', 1),
                (303, '处理流程', 300, 2, 'sop', 'claim/sop/index', 'C', 'claim:sop:list', 'FileTextOutlined', 0, 0, '查看和编辑SOP', 1),
                (304, '流程编辑', 303, 1, '', '', 'F', 'claim:sop:edit', '#', 0, 0, '', 1),
                (305, '历史案例', 300, 3, 'history', 'claim/history/index', 'C', 'claim:history:list', 'BookOutlined', 0, 0, '查看和编辑历史案例', 1),
                (306, '案例编辑', 305, 1, '', '', 'F', 'claim:history:edit', '#', 0, 0, '', 1),
                (307, '理赔机器人', 300, 4, 'robot', 'claim/robot/index', 'C', 'claim:robot:list', 'RobotOutlined', 0, 0, '单独的理赔机器人凭证', 1),
                (308, '机器人编辑', 307, 1, '', '', 'F', 'claim:robot:edit', '#', 0, 0, '', 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name),
                  parent_id = VALUES(parent_id),
                  sort_order = VALUES(sort_order),
                  path = VALUES(path),
                  component = VALUES(component),
                  menu_type = VALUES(menu_type),
                  perms = VALUES(perms),
                  icon = VALUES(icon),
                  remark = VALUES(remark),
                  is_active = 1
                """);
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT r.role_id, m.menu_id, 1
                FROM sys_role r
                JOIN sys_menu m ON m.menu_id BETWEEN 300 AND 308
                WHERE r.is_active = 1
                """);
        log.info("理赔档案、SOP、历史案例表与菜单已同步");
    }
}
