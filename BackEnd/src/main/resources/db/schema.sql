-- Base Admin Management System Database Schema
CREATE DATABASE IF NOT EXISTS base_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE base_admin;

-- System users
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  user_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'User ID',
  username      VARCHAR(50)  NOT NULL                  COMMENT 'Login username',
  password      VARCHAR(200) NOT NULL                  COMMENT 'BCrypt hashed password',
  nickname      VARCHAR(50)  DEFAULT ''                COMMENT 'Display name',
  email         VARCHAR(100) DEFAULT ''                COMMENT 'Email',
  phone         VARCHAR(20)  DEFAULT ''                COMMENT 'Phone number',
  avatar        VARCHAR(500) DEFAULT ''                COMMENT 'Avatar URL',
  gender        TINYINT      DEFAULT 0                 COMMENT 'Gender: 0=unknown, 1=male, 2=female',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  is_active     TINYINT      NOT NULL DEFAULT 1        COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System users';

-- System roles
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  role_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Role ID',
  role_name     VARCHAR(50)  NOT NULL                  COMMENT 'Role name',
  role_key      VARCHAR(100) NOT NULL                  COMMENT 'Role key',
  sort_order    INT          DEFAULT 0                 COMMENT 'Sort order',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  is_active     TINYINT      NOT NULL DEFAULT 1        COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System roles';

-- System menus and permissions
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  menu_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Menu ID',
  menu_name     VARCHAR(50)  NOT NULL                  COMMENT 'Menu name',
  parent_id     BIGINT       DEFAULT 0                 COMMENT 'Parent menu ID (0=root)',
  sort_order    INT          DEFAULT 0                 COMMENT 'Sort order',
  path          VARCHAR(200) DEFAULT ''                COMMENT 'Route path',
  component     VARCHAR(200) DEFAULT ''                COMMENT 'Frontend component path',
  menu_type     CHAR(1)      NOT NULL                  COMMENT 'Type: M=directory, C=menu, F=button',
  perms         VARCHAR(100) DEFAULT ''                COMMENT 'Permission string',
  icon          VARCHAR(100) DEFAULT '#'               COMMENT 'Menu icon',
  visible       TINYINT      DEFAULT 0                 COMMENT 'Visible: 0=yes, 1=no',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=active, 1=disabled',
  create_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Created by',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  update_by     VARCHAR(50)  DEFAULT ''                COMMENT 'Updated by',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  remark        VARCHAR(500) DEFAULT ''                COMMENT 'Remark',
  is_active     TINYINT      NOT NULL DEFAULT 1        COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System menus and permissions';

-- User-Role association
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  user_id  BIGINT NOT NULL COMMENT 'User ID',
  role_id  BIGINT NOT NULL COMMENT 'Role ID',
  is_active TINYINT NOT NULL DEFAULT 1 COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-Role association';

-- Role-Menu association
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  role_id  BIGINT NOT NULL COMMENT 'Role ID',
  menu_id  BIGINT NOT NULL COMMENT 'Menu ID',
  is_active TINYINT NOT NULL DEFAULT 1 COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Role-Menu association';

-- Login log
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
  log_id        BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Log ID',
  username      VARCHAR(50)  DEFAULT ''                COMMENT 'Login username',
  ip            VARCHAR(128) DEFAULT ''                COMMENT 'Login IP',
  location      VARCHAR(255) DEFAULT ''                COMMENT 'Login location',
  browser       VARCHAR(50)  DEFAULT ''                COMMENT 'Browser',
  os            VARCHAR(50)  DEFAULT ''                COMMENT 'OS',
  status        TINYINT      DEFAULT 0                 COMMENT 'Status: 0=success, 1=fail',
  msg           VARCHAR(255) DEFAULT ''                COMMENT 'Message',
  login_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Login time',
  is_active     TINYINT      NOT NULL DEFAULT 1        COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (log_id),
  INDEX idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Login log';

-- Operation log
DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  log_id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Log ID',
  title          VARCHAR(50)  DEFAULT ''                COMMENT 'Module title',
  business_type  TINYINT      DEFAULT 0                 COMMENT 'Business type: 0=other, 1=add, 2=edit, 3=delete',
  method         VARCHAR(200) DEFAULT ''                COMMENT 'Method name',
  request_method VARCHAR(10)  DEFAULT ''                COMMENT 'HTTP method',
  oper_name      VARCHAR(50)  DEFAULT ''                COMMENT 'Operator name',
  oper_url       VARCHAR(500) DEFAULT ''                COMMENT 'Request URL',
  oper_ip        VARCHAR(128) DEFAULT ''                COMMENT 'Operator IP',
  oper_param     TEXT                                    COMMENT 'Request params',
  json_result    TEXT                                    COMMENT 'Response result',
  status         TINYINT      DEFAULT 0                 COMMENT 'Status: 0=success, 1=fail',
  error_msg      TEXT                                    COMMENT 'Error message',
  oper_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Operation time',
  cost_time      BIGINT       DEFAULT 0                 COMMENT 'Cost time (ms)',
  is_active      TINYINT      NOT NULL DEFAULT 1       COMMENT 'Soft delete: 1=active 0=deleted',
  PRIMARY KEY (log_id),
  INDEX idx_oper_time (oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operation log';

-- =============================================
-- Seed Data
-- =============================================

-- Default admin role
INSERT INTO sys_role (role_id, role_name, role_key, sort_order, status, remark)
VALUES (1, '超级管理员', 'admin', 1, 0, '超级管理员拥有所有权限');

-- Default admin user (password: admin123)
INSERT INTO sys_user (user_id, username, password, nickname, status, remark)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 0, '系统管理员');

-- Assign admin role
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- System Management directory
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(1, '系统管理', 0, 1, '/system', '', 'M', '', 'SettingOutlined');

-- Menu items
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(2, '用户管理', 1, 1, 'user', 'system/user/index', 'C', 'system:user:list', 'UserOutlined'),
(3, '角色管理', 1, 2, 'role', 'system/role/index', 'C', 'system:role:list', 'TeamOutlined'),
(4, '菜单管理', 1, 3, 'menu', 'system/menu/index', 'C', 'system:menu:list', 'MenuOutlined');

-- User management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(5, '用户新增', 2, 1, '', '', 'F', 'system:user:add', '#'),
(6, '用户修改', 2, 2, '', '', 'F', 'system:user:edit', '#'),
(7, '用户删除', 2, 3, '', '', 'F', 'system:user:delete', '#'),
(8, '重置密码', 2, 4, '', '', 'F', 'system:user:resetPwd', '#');

-- Role management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(9, '角色新增', 3, 1, '', '', 'F', 'system:role:add', '#'),
(10, '角色修改', 3, 2, '', '', 'F', 'system:role:edit', '#'),
(11, '角色删除', 3, 3, '', '', 'F', 'system:role:delete', '#');

-- Menu management buttons
INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(12, '菜单新增', 4, 1, '', '', 'F', 'system:menu:add', '#'),
(13, '菜单修改', 4, 2, '', '', 'F', 'system:menu:edit', '#'),
(14, '菜单删除', 4, 3, '', '', 'F', 'system:menu:delete', '#');

-- Assign ALL menus to admin role
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14);

-- 钉钉扫码自动注册的默认角色
INSERT INTO sys_role (role_name, role_key, sort_order, status, remark)
SELECT '普通账号', 'hr_user', 2, 0, '钉钉扫码自动注册的默认角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'hr_user');

-- AI 厂商配置
CREATE TABLE IF NOT EXISTS sys_ai_provider (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  provider       VARCHAR(32)   NOT NULL                COMMENT '厂商标识',
  provider_name  VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '展示名',
  api_key        VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT 'API Key',
  model          VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '模型名',
  base_url       VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '兼容接口地址',
  enabled        TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用',
  sort_order     INT           NOT NULL DEFAULT 0      COMMENT '排序',
  remark         VARCHAR(500)  DEFAULT ''              COMMENT '备注',
  create_by      VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI厂商配置';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(140, 'AI模型配置', 1, 4, 'ai-provider', 'system/ai-provider/index', 'C', 'system:ai:list', 'KeyOutlined'),
(141, 'AI模型编辑', 140, 1, '', '', 'F', 'system:ai:edit', '#');

-- 钉钉应用配置与扫码身份
CREATE TABLE IF NOT EXISTS sys_dingtalk_app (
  id             BIGINT       NOT NULL                COMMENT '固定为 1',
  app_id         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '钉钉 AppId',
  agent_id       VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '钉钉 AgentId',
  client_id      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT 'Client ID',
  client_secret  VARCHAR(256) NOT NULL DEFAULT ''     COMMENT 'Client Secret',
  corp_id        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '企业 CorpId',
  enabled        TINYINT      NOT NULL DEFAULT 0      COMMENT '是否启用',
  create_by      VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉企业内部应用配置';

INSERT IGNORE INTO sys_dingtalk_app (id, app_id, agent_id, client_id, client_secret, enabled, create_by, is_active)
VALUES (1, '', '', '', '', 0, 'system', 1);

CREATE TABLE IF NOT EXISTS hr_user_dingtalk (
  user_id            BIGINT      NOT NULL COMMENT '系统用户',
  dingtalk_user_id   VARCHAR(64) NOT NULL COMMENT '企业userid',
  dingtalk_union_id  VARCHAR(64) NOT NULL COMMENT 'unionId',
  create_by          VARCHAR(50) DEFAULT '' COMMENT '创建者',
  create_time        DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50) DEFAULT '' COMMENT '更新者',
  update_time        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT     NOT NULL DEFAULT 1 COMMENT '是否有效',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钉钉身份';

CREATE TABLE IF NOT EXISTS sys_user_online (
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  token_id    VARCHAR(64)  NOT NULL COMMENT 'JWT jti',
  username    VARCHAR(50)  DEFAULT '' COMMENT '用户名',
  ip          VARCHAR(128) DEFAULT '' COMMENT '登录IP',
  user_agent  VARCHAR(512) DEFAULT '' COMMENT 'User-Agent',
  login_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  expire_time DATETIME     NOT NULL COMMENT '过期时间',
  is_active   TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
  PRIMARY KEY (user_id),
  KEY idx_token_id (token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线会话';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon) VALUES
(142, '钉钉应用配置', 1, 5, 'dingtalk', 'system/dingtalk/index', 'C', 'system:dingtalk:list', 'LinkOutlined'),
(143, '钉钉应用编辑', 142, 1, '', '', 'F', 'system:dingtalk:edit', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1,140),(1,141),(1,142),(1,143);

-- 算薪：部门、岗位、事假口径、员工档案
CREATE TABLE IF NOT EXISTS pay_dept (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  dept_name   VARCHAR(50)  NOT NULL                COMMENT '部门名称',
  sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序',
  status      TINYINT      NOT NULL DEFAULT 0      COMMENT '0正常 1停用',
  dingtalk_dept_id BIGINT   NOT NULL DEFAULT 0      COMMENT '钉钉部门ID，0表示手工维护',
  create_by   VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active   TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_ding_dept (dingtalk_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪部门';

CREATE TABLE IF NOT EXISTS pay_post (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  post_name   VARCHAR(50)  NOT NULL                COMMENT '岗位名称',
  sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序',
  status      TINYINT      NOT NULL DEFAULT 0      COMMENT '0正常 1停用',
  create_by   VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active   TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪岗位';

CREATE TABLE IF NOT EXISTS pay_grade (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '职级ID',
  grade_name  VARCHAR(50)  NOT NULL                COMMENT '职级名称',
  sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序',
  status      TINYINT      NOT NULL DEFAULT 0      COMMENT '0正常 1停用',
  create_by   VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active   TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪职级';

CREATE TABLE IF NOT EXISTS pay_late_rule (
  id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  grade_id            BIGINT        NOT NULL                COMMENT '职级',
  allow_count         INT           NOT NULL DEFAULT 3      COMMENT '事假2起算门槛',
  personal1_amount    DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '事假1整月定额',
  personal2_amount    DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '事假2每次扣款',
  reward_amount       DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '兼容旧字段：通报嘉奖一次',
  punish_amount       DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '兼容旧字段：通报批评一次',
  amt_verbal_praise   DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '口头表扬一次',
  amt_commendation    DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '通报嘉奖一次',
  amt_merit           DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '记功一次',
  amt_major_merit     DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '记大功一次',
  amt_verbal_critic   DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '口头批评一次',
  amt_bulletin_critic DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '通报批评一次',
  amt_demerit         DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '记过一次',
  amt_major_demerit   DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '记大过一次',
  create_by           VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_late_grade (grade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职级事假与通报奖惩单价';

CREATE TABLE IF NOT EXISTS pay_employee (
  id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '员工ID',
  dept_id             BIGINT        NOT NULL                COMMENT '部门',
  post_id             BIGINT        NOT NULL                COMMENT '岗位',
  grade_id            BIGINT        NOT NULL DEFAULT 0      COMMENT '职级，0未设置',
  employee_name       VARCHAR(50)   NOT NULL                COMMENT '姓名',
  hire_date           DATE          NOT NULL                COMMENT '入职时间',
  salary_standard     DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '工资标准=底薪构成合计',
  amt_post            DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '岗位工资',
  amt_full_attendance DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '全勤工资',
  amt_social_subsidy  DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '社会保险补贴',
  amt_housing_subsidy DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '公积金补贴',
  amt_transport       DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '交通通讯补助',
  amt_noncompete      DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '竞业费',
  amt_hygiene         DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '洗理费',
  amt_fixed_overtime  DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '固定加班费',
  employee_no         VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '工号',
  phone               VARCHAR(20)   NOT NULL DEFAULT ''     COMMENT '手机号',
  id_card             VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '身份证号',
  bank_account        VARCHAR(40)   NOT NULL DEFAULT ''     COMMENT '银行卡号',
  bank_name           VARCHAR(80)   NOT NULL DEFAULT ''     COMMENT '开户行',
  job_status          TINYINT       NOT NULL DEFAULT 1      COMMENT '1在职 0离职',
  hukou_type          VARCHAR(10)   NOT NULL DEFAULT ''     COMMENT '户口性质：城镇/农村',
  social_base         DECIMAL(12,2) NULL                    COMMENT '社保缴费基数',
  social_account      VARCHAR(40)   NOT NULL DEFAULT ''     COMMENT '个人社保账号',
  dingtalk_user_id    VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '钉钉企业用户ID',
  create_by           VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_dept (dept_id),
  KEY idx_post (post_id),
  KEY idx_grade (grade_id),
  KEY idx_ding_user (dingtalk_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪员工';

CREATE TABLE IF NOT EXISTS pay_social_rule (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_code    VARCHAR(32)   NOT NULL                COMMENT 'sh_urban/sh_rural/xj_urban/xj_rural',
  region_name  VARCHAR(20)   NOT NULL                COMMENT '地区',
  hukou_type   VARCHAR(10)   NOT NULL                COMMENT '城镇/农村',
  base_min     DECIMAL(12,2) NOT NULL                COMMENT '缴费基数下限',
  base_max     DECIMAL(12,2) NOT NULL                COMMENT '缴费基数上限',
  remark       VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '口径说明',
  sort_order   INT           NOT NULL DEFAULT 0      COMMENT '排序',
  create_by    VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_rule (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社保缴纳规则';

CREATE TABLE IF NOT EXISTS pay_social_rule_item (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_id        BIGINT       NOT NULL                COMMENT '规则ID',
  item_code      VARCHAR(32)  NOT NULL                COMMENT '险种编码',
  item_name      VARCHAR(40)  NOT NULL                COMMENT '险种名称',
  employer_rate  DECIMAL(6,2) NOT NULL DEFAULT 0      COMMENT '单位比例，百分数',
  personal_rate  DECIMAL(6,2) NOT NULL DEFAULT 0      COMMENT '个人比例，百分数',
  base_min       DECIMAL(12,2) NULL                   COMMENT '本险种基数下限，空则用社保基数',
  base_max       DECIMAL(12,2) NULL                   COMMENT '本险种基数上限，空则用社保基数',
  sort_order     INT          NOT NULL DEFAULT 0      COMMENT '排序',
  is_active      TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_item (rule_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社保缴纳规则险种';

CREATE TABLE IF NOT EXISTS pay_fee_category (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  category_name VARCHAR(40)  NOT NULL                COMMENT '费用类目',
  month_rule    VARCHAR(10)  NOT NULL DEFAULT ''     COMMENT 'hire=归属入职月且不可改',
  fee_kind      VARCHAR(10)  NOT NULL DEFAULT 'deduct' COMMENT 'add加钱类 deduct扣费类',
  sort_order    INT          NOT NULL DEFAULT 0      COMMENT '排序',
  create_by     VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active     TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工额外费用类目';

CREATE TABLE IF NOT EXISTS pay_employee_fee (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  employee_id  BIGINT        NOT NULL                COMMENT '员工',
  category_id  BIGINT        NOT NULL                COMMENT '费用类目',
  fee_month    CHAR(7)       NOT NULL DEFAULT ''     COMMENT '归属月份 yyyy-MM',
  amount       DECIMAL(12,2) NOT NULL                COMMENT '金额',
  image_url    VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '截图路径',
  remark       VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
  create_by    VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_fee_employee (employee_id),
  KEY idx_fee_category (category_id),
  KEY idx_fee_month (fee_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工额外费用';

CREATE TABLE IF NOT EXISTS pay_performance (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  employee_id  BIGINT        NOT NULL                COMMENT '员工',
  period       CHAR(7)       NOT NULL                COMMENT '考核月份 yyyy-MM',
  coefficient  DECIMAL(8,4)  NOT NULL DEFAULT 1.0000 COMMENT '绩效考核系数',
  remark       VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
  create_by    VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_perf_emp_period (employee_id, period),
  KEY idx_perf_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度绩效考核系数';

CREATE TABLE IF NOT EXISTS pay_reward (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  period          CHAR(7)       NOT NULL                COMMENT '算薪月份 yyyy-MM',
  employee_id     BIGINT        NOT NULL                COMMENT '员工',
  bulletin_id     BIGINT        NULL                    COMMENT '关联行政通报',
  blackboard_id   VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '钉钉公告ID',
  reward_kind     VARCHAR(20)   NOT NULL                COMMENT '口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过',
  reason          VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '事由',
  times           INT           NOT NULL DEFAULT 1      COMMENT '次数',
  unit_amount     DECIMAL(12,2) NULL                    COMMENT '职级单价',
  amount          DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '最终金额，奖为正惩为负',
  amount_source   VARCHAR(20)   NOT NULL DEFAULT 'grade' COMMENT 'bulletin明文/grade职级/manual手工',
  remark          VARCHAR(500)  NOT NULL DEFAULT ''     COMMENT '备注',
  create_by       VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_reward_period (period),
  KEY idx_reward_employee (employee_id),
  KEY idx_reward_bulletin (bulletin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度行政通报奖惩';

CREATE TABLE IF NOT EXISTS pay_attendance_summary (
  id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  period                CHAR(7)       NOT NULL                COMMENT '算薪月份',
  employee_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '员工',
  dingtalk_user_id      VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '钉钉用户ID',
  employee_name         VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '姓名',
  dept_name             VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '部门',
  late_early_count      INT           NOT NULL DEFAULT 0      COMMENT '迟到早退次数',
  personal1_count       INT           NOT NULL DEFAULT 0      COMMENT '事假1次数(0或1)',
  personal2_count       INT           NOT NULL DEFAULT 0      COMMENT '事假2次数',
  absent_count          INT           NOT NULL DEFAULT 0      COMMENT '旷工次数',
  missing_card_count    INT           NOT NULL DEFAULT 0      COMMENT '缺卡次数',
  annual_leave_days     DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '年假天数',
  special_leave_days    DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '婚丧产假天数',
  actual_rest_days      DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '本月实休(调休使用)',
  remaining_comp_days   DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '剩余调休',
  create_time           DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_att_sum (period, dingtalk_user_id),
  KEY idx_att_sum_emp (employee_id),
  KEY idx_att_sum_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度考勤汇总';

CREATE TABLE IF NOT EXISTS pay_attendance (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  period           CHAR(7)      NOT NULL                COMMENT '核算月份 yyyy-MM',
  dingtalk_user_id VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '钉钉用户ID',
  employee_name    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '姓名',
  dept_name        VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '部门',
  work_date        DATE         NOT NULL                COMMENT '工作日',
  check_type       VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT 'OnDuty上班 OffDuty下班',
  time_result      VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '打卡结果',
  location_result  VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '位置结果',
  user_check_time  DATETIME     NULL                    COMMENT '实际打卡时间',
  base_check_time  DATETIME     NULL                    COMMENT '应打卡时间',
  source_id        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '钉钉记录ID',
  PRIMARY KEY (id),
  KEY idx_att_period (period, work_date),
  KEY idx_att_user (dingtalk_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉考勤打卡结果';

CREATE TABLE IF NOT EXISTS pay_bulletin (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  period         CHAR(7)      NOT NULL                COMMENT '核算月份 yyyy-MM',
  blackboard_id  VARCHAR(64)  NOT NULL                COMMENT '钉钉公告ID',
  title          VARCHAR(200) NOT NULL DEFAULT ''     COMMENT '标题',
  category_name  VARCHAR(80)  NOT NULL DEFAULT ''     COMMENT '分类',
  author         VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '发布人',
  bulletin_kind  VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '口头表扬等八类或通报',
  plain_content  MEDIUMTEXT                           COMMENT '正文纯文本',
  publish_time   DATETIME     NULL                    COMMENT '发布时间',
  receiver_text  VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '接收范围',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bulletin_period (period, blackboard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉行政通报';

CREATE TABLE IF NOT EXISTS pay_dingtalk_pull (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  pull_type    VARCHAR(20) NOT NULL                COMMENT 'attendance/bulletin',
  period       CHAR(7)     NOT NULL                COMMENT '核算月份',
  record_count INT         NOT NULL DEFAULT 0      COMMENT '条数',
  synced_at    DATETIME    NOT NULL                COMMENT '同步时间',
  synced_by    VARCHAR(50) NOT NULL DEFAULT ''     COMMENT '操作人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pull (pull_type, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉考勤与通报同步记录';

CREATE TABLE IF NOT EXISTS pay_work_calendar (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  cal_year       INT         NOT NULL                COMMENT '年份',
  cal_month      INT         NOT NULL                COMMENT '月份',
  calendar_days  INT         NOT NULL                COMMENT '日历天数',
  holiday_name   VARCHAR(20) NOT NULL DEFAULT ''     COMMENT '法定假名称',
  holiday_days   INT         NOT NULL DEFAULT 0      COMMENT '法定假天数',
  dual_rest      INT         NOT NULL DEFAULT 0      COMMENT '双休休息',
  dual_work      INT         NOT NULL DEFAULT 0      COMMENT '双休出勤',
  dual_pay       INT         NOT NULL DEFAULT 0      COMMENT '双休计薪',
  single_rest    INT         NOT NULL DEFAULT 0      COMMENT '单休休息',
  single_work    INT         NOT NULL DEFAULT 0      COMMENT '单休出勤',
  single_pay     INT         NOT NULL DEFAULT 0      COMMENT '单休计薪',
  alt_rest       INT         NOT NULL DEFAULT 0      COMMENT '大小周休息',
  alt_work       INT         NOT NULL DEFAULT 0      COMMENT '大小周出勤',
  alt_pay        INT         NOT NULL DEFAULT 0      COMMENT '大小周计薪',
  PRIMARY KEY (id),
  UNIQUE KEY uk_work_calendar (cal_year, cal_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计薪天数';
