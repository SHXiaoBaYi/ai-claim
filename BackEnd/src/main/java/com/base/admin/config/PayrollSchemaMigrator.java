package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class PayrollSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪部门'
                """);
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪岗位'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_leave_rule (
                  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  rule_code   VARCHAR(32)  NOT NULL                COMMENT 'personal_1/personal_2',
                  title       VARCHAR(50)  NOT NULL                COMMENT '显示名',
                  meaning     VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '统一口径',
                  unit        VARCHAR(16)  NOT NULL DEFAULT 'day'  COMMENT 'day天 hour小时',
                  deduct_pay  TINYINT      NOT NULL DEFAULT 1      COMMENT '1扣薪 0不扣薪',
                  sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序',
                  create_by   VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by   VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active   TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_rule_code (rule_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事假口径'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_employee (
                  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '员工ID',
                  dept_id          BIGINT        NOT NULL                COMMENT '部门',
                  post_id          BIGINT        NOT NULL                COMMENT '岗位',
                  employee_name    VARCHAR(50)   NOT NULL                COMMENT '姓名',
                  hire_date        DATE          NOT NULL                COMMENT '入职时间',
                  salary_standard  DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '工资标准',
                  employee_no      VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '工号',
                  phone            VARCHAR(20)   NOT NULL DEFAULT ''     COMMENT '手机号',
                  id_card          VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '身份证号',
                  bank_account     VARCHAR(40)   NOT NULL DEFAULT ''     COMMENT '银行卡号',
                  bank_name        VARCHAR(80)   NOT NULL DEFAULT ''     COMMENT '开户行',
                  job_status       TINYINT       NOT NULL DEFAULT 1      COMMENT '1在职 0离职',
                  dingtalk_user_id VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '钉钉企业用户ID',
                  create_by        VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
                  create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by        VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
                  update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active        TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  KEY idx_dept (dept_id),
                  KEY idx_post (post_id),
                  KEY idx_ding_user (dingtalk_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪员工'
                """);
        ensureColumn("pay_dept", "dingtalk_dept_id",
                "ALTER TABLE pay_dept ADD COLUMN dingtalk_dept_id BIGINT NOT NULL DEFAULT 0 COMMENT '钉钉部门ID，0表示手工维护' AFTER status");
        ensureIndex("pay_dept", "idx_ding_dept", "ALTER TABLE pay_dept ADD INDEX idx_ding_dept (dingtalk_dept_id)");
        ensureColumn("pay_employee", "dingtalk_user_id",
                "ALTER TABLE pay_employee ADD COLUMN dingtalk_user_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '钉钉企业用户ID' AFTER job_status");
        ensureIndex("pay_employee", "idx_ding_user", "ALTER TABLE pay_employee ADD INDEX idx_ding_user (dingtalk_user_id)");
        ensureColumn("pay_employee", "hukou_type",
                "ALTER TABLE pay_employee ADD COLUMN hukou_type VARCHAR(10) NOT NULL DEFAULT '' COMMENT '户口性质：城镇/农村' AFTER job_status");
        ensureColumn("pay_employee", "social_base",
                "ALTER TABLE pay_employee ADD COLUMN social_base DECIMAL(12,2) NULL COMMENT '社保缴费基数' AFTER hukou_type");
        ensureColumn("pay_employee", "social_account",
                "ALTER TABLE pay_employee ADD COLUMN social_account VARCHAR(40) NOT NULL DEFAULT '' COMMENT '个人社保账号' AFTER social_base");
        ensureColumn("pay_employee", "grade_id",
                "ALTER TABLE pay_employee ADD COLUMN grade_id BIGINT NOT NULL DEFAULT 0 COMMENT '职级，0未设置' AFTER post_id");
        ensureIndex("pay_employee", "idx_grade", "ALTER TABLE pay_employee ADD INDEX idx_grade (grade_id)");
        ensureSalaryCompositionColumns();
        createGradeTables();
        createSocialTables();
        seedSocialRules();
        createFeeTables();
        createPerformanceTable();
        createRewardTable();
        widenRewardKind();
        createAttendanceSummaryTable();
        createPullTables();
        createWorkCalendar();
        jdbc.update("""
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES
                (200, '算薪', 0, 2, '/payroll', '', 'M', '', 'AccountBookOutlined', 0, 0, '员工档案与算薪基础资料', 1),
                (201, '员工管理', 200, 1, 'employee', 'payroll/employee/index', 'C', 'payroll:employee:list', 'TeamOutlined', 0, 0, '', 1),
                (202, '员工编辑', 201, 1, '', '', 'F', 'payroll:employee:edit', '#', 0, 0, '', 1),
                (207, '部门', 200, 2, 'dept', 'payroll/dept/index', 'C', 'payroll:base:list', 'BankOutlined', 0, 0, '维护部门', 1),
                (208, '部门编辑', 207, 1, '', '', 'F', 'payroll:base:edit', '#', 0, 0, '', 1),
                (209, '岗位', 200, 3, 'post', 'payroll/post/index', 'C', 'payroll:base:list', 'IdcardOutlined', 0, 0, '维护岗位', 1),
                (210, '岗位编辑', 209, 1, '', '', 'F', 'payroll:base:edit', '#', 0, 0, '', 1),
                (219, '职级', 200, 4, 'grade', 'payroll/grade/index', 'C', 'payroll:base:list', 'TrophyOutlined', 0, 0, '维护职级，事假扣款按职级配置', 1),
                (220, '职级编辑', 219, 1, '', '', 'F', 'payroll:base:edit', '#', 0, 0, '', 1),
                (203, '职级规则', 200, 5, 'base', 'payroll/base/index', 'C', 'payroll:base:list', 'ProfileOutlined', 0, 0, '按职级配置事假扣款与通报奖惩单价', 1),
                (204, '职级规则编辑', 203, 1, '', '', 'F', 'payroll:base:edit', '#', 0, 0, '', 1),
                (205, '社保缴纳规则', 200, 6, 'social', 'payroll/social/index', 'C', 'payroll:social:list', 'SafetyCertificateOutlined', 0, 0, '上海、新疆城镇与农村社保比例', 1),
                (206, '社保规则编辑', 205, 1, '', '', 'F', 'payroll:social:edit', '#', 0, 0, '', 1),
                (211, '额外费用', 200, 7, 'fee', 'payroll/fee/index', 'C', 'payroll:fee:list', 'MoneyCollectOutlined', 0, 0, '核算工资时维护员工额外费用', 1),
                (212, '额外费用编辑', 211, 1, '', '', 'F', 'payroll:fee:edit', '#', 0, 0, '', 1),
                (221, '绩效考核', 200, 8, 'performance', 'payroll/performance/index', 'C', 'payroll:performance:list', 'FundOutlined', 0, 0, '按月维护员工绩效考核系数', 1),
                (222, '绩效考核编辑', 221, 1, '', '', 'F', 'payroll:performance:edit', '#', 0, 0, '', 1),
                (213, '考勤记录', 200, 9, 'attendance', 'payroll/attendance/index', 'C', 'payroll:attendance:list', 'CalendarOutlined', 0, 0, '钉钉考勤，核算工资前同步', 1),
                (214, '考勤同步', 213, 1, '', '', 'F', 'payroll:attendance:sync', '#', 0, 0, '', 1),
                (215, '行政通报', 200, 10, 'bulletin', 'payroll/bulletin/index', 'C', 'payroll:bulletin:list', 'NotificationOutlined', 0, 0, '钉钉行政通报原文', 1),
                (216, '通报同步', 215, 1, '', '', 'F', 'payroll:bulletin:sync', '#', 0, 0, '', 1),
                (223, '通报奖惩', 200, 11, 'reward', 'payroll/reward/index', 'C', 'payroll:reward:list', 'StarOutlined', 0, 0, '按月维护员工行政通报奖惩金额', 1),
                (224, '通报奖惩编辑', 223, 1, '', '', 'F', 'payroll:reward:edit', '#', 0, 0, '', 1),
                (217, '计薪天数', 200, 12, 'calendar', 'payroll/calendar/index', 'C', 'payroll:calendar:list', 'ScheduleOutlined', 0, 0, '按国务院放假安排生成，可改休息出勤计薪', 1),
                (218, '计薪天数编辑', 217, 1, '', '', 'F', 'payroll:calendar:edit', '#', 0, 0, '', 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name),
                  parent_id = VALUES(parent_id),
                  sort_order = VALUES(sort_order),
                  path = VALUES(path),
                  component = VALUES(component),
                  menu_type = VALUES(menu_type),
                  perms = VALUES(perms),
                  icon = VALUES(icon),
                  visible = 0,
                  status = 0,
                  remark = VALUES(remark),
                  is_active = 1
                """);
        // 发版被旧包冲掉后，菜单行可能还在但角色授权被关掉，强制给超管加回
        jdbc.update("""
                INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active)
                SELECT 1, menu_id, 1 FROM sys_menu WHERE menu_id IN (200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224)
                """);
        jdbc.update("""
                UPDATE sys_role_menu
                SET is_active = 1
                WHERE role_id = 1
                  AND menu_id IN (200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224)
                """);
        Integer menuCount = jdbc.queryForObject("""
                SELECT COUNT(1) FROM sys_menu
                WHERE is_active = 1 AND menu_id BETWEEN 200 AND 224
                """, Integer.class);
        Integer roleMenuCount = jdbc.queryForObject("""
                SELECT COUNT(1) FROM sys_role_menu
                WHERE is_active = 1 AND role_id = 1 AND menu_id BETWEEN 200 AND 224
                """, Integer.class);
        log.info("算薪菜单已恢复 menu={} roleMenu={}", menuCount, roleMenuCount);
        log.info("算薪部门、岗位、职级、事假规则、社保规则与员工档案已同步");
    }

    private void createGradeTables() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算薪职级'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_late_rule (
                  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                  grade_id          BIGINT        NOT NULL                COMMENT '职级',
                  allow_count       INT           NOT NULL DEFAULT 3      COMMENT '事假2起算门槛：迟到早退超过该次数才记事假2',
                  personal1_amount  DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '事假1整月定额，当月有迟到早退即扣，超门槛仍扣',
                  personal2_amount  DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '事假2每次扣款',
                  reward_amount       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '兼容旧字段：通报嘉奖',
                  punish_amount       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '兼容旧字段：通报批评',
                  amt_verbal_praise   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '口头表扬一次',
                  amt_commendation    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '通报嘉奖一次',
                  amt_merit           DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记功一次',
                  amt_major_merit     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记大功一次',
                  amt_verbal_critic   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '口头批评一次',
                  amt_bulletin_critic DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '通报批评一次',
                  amt_demerit         DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记过一次',
                  amt_major_demerit   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记大过一次',
                  create_by         VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
                  create_time       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by         VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
                  update_time       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active         TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_late_grade (grade_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职级事假与通报奖惩单价'
                """);
        ensureColumn("pay_late_rule", "reward_amount",
                "ALTER TABLE pay_late_rule ADD COLUMN reward_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '兼容旧字段：通报嘉奖' AFTER personal2_amount");
        ensureColumn("pay_late_rule", "punish_amount",
                "ALTER TABLE pay_late_rule ADD COLUMN punish_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '兼容旧字段：通报批评' AFTER reward_amount");
        ensureColumn("pay_late_rule", "amt_verbal_praise",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_verbal_praise DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '口头表扬一次' AFTER punish_amount");
        ensureColumn("pay_late_rule", "amt_commendation",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_commendation DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '通报嘉奖一次' AFTER amt_verbal_praise");
        ensureColumn("pay_late_rule", "amt_merit",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_merit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记功一次' AFTER amt_commendation");
        ensureColumn("pay_late_rule", "amt_major_merit",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_major_merit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记大功一次' AFTER amt_merit");
        ensureColumn("pay_late_rule", "amt_verbal_critic",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_verbal_critic DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '口头批评一次' AFTER amt_major_merit");
        ensureColumn("pay_late_rule", "amt_bulletin_critic",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_bulletin_critic DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '通报批评一次' AFTER amt_verbal_critic");
        ensureColumn("pay_late_rule", "amt_demerit",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_demerit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记过一次' AFTER amt_bulletin_critic");
        ensureColumn("pay_late_rule", "amt_major_demerit",
                "ALTER TABLE pay_late_rule ADD COLUMN amt_major_demerit DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '记大过一次' AFTER amt_demerit");
        jdbc.update("""
                UPDATE pay_late_rule
                SET amt_commendation = reward_amount
                WHERE is_active = 1 AND amt_commendation = 0 AND reward_amount <> 0
                """);
        jdbc.update("""
                UPDATE pay_late_rule
                SET amt_bulletin_critic = punish_amount
                WHERE is_active = 1 AND amt_bulletin_critic = 0 AND punish_amount <> 0
                """);
    }

    private void createFeeTables() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工额外费用类目'
                """);
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工额外费用'
                """);
        ensureColumn("pay_employee_fee", "fee_month",
                "ALTER TABLE pay_employee_fee ADD COLUMN fee_month CHAR(7) NOT NULL DEFAULT '' COMMENT '归属月份 yyyy-MM' AFTER category_id");
        ensureIndex("pay_employee_fee", "idx_fee_month",
                "ALTER TABLE pay_employee_fee ADD INDEX idx_fee_month (fee_month)");
        ensureColumn("pay_fee_category", "month_rule",
                "ALTER TABLE pay_fee_category ADD COLUMN month_rule VARCHAR(10) NOT NULL DEFAULT '' COMMENT 'hire=归属入职月且不可改' AFTER category_name");
        ensureColumn("pay_fee_category", "fee_kind",
                "ALTER TABLE pay_fee_category ADD COLUMN fee_kind VARCHAR(10) NOT NULL DEFAULT 'deduct' COMMENT 'add加钱类 deduct扣费类' AFTER month_rule");
        jdbc.update("""
                INSERT INTO pay_fee_category (category_name, month_rule, fee_kind, sort_order, create_by, is_active)
                SELECT '新入职人员碳14费用', 'hire', 'deduct', 1, 'system', 1
                WHERE NOT EXISTS (
                  SELECT 1 FROM pay_fee_category WHERE category_name = '新入职人员碳14费用' AND is_active = 1
                )
                """);
        jdbc.update("""
                INSERT INTO pay_fee_category (category_name, month_rule, fee_kind, sort_order, create_by, is_active)
                SELECT '个税', '', 'deduct', 2, 'system', 1
                WHERE NOT EXISTS (
                  SELECT 1 FROM pay_fee_category WHERE category_name = '个税' AND is_active = 1
                )
                """);
        jdbc.update("""
                UPDATE pay_fee_category
                SET month_rule = 'hire'
                WHERE category_name = '新入职人员碳14费用' AND is_active = 1 AND month_rule = ''
                """);
        jdbc.update("""
                UPDATE pay_fee_category
                SET fee_kind = 'deduct'
                WHERE is_active = 1 AND (fee_kind IS NULL OR fee_kind = '')
                """);
        jdbc.update("""
                UPDATE pay_fee_category
                SET fee_kind = 'deduct'
                WHERE category_name = '个税' AND is_active = 1 AND fee_kind <> 'deduct'
                """);
    }

    private void createPerformanceTable() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度绩效考核系数'
                """);
    }

    private void ensureSalaryCompositionColumns() {
        ensureColumn("pay_employee", "amt_post",
                "ALTER TABLE pay_employee ADD COLUMN amt_post DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '岗位工资' AFTER salary_standard");
        ensureColumn("pay_employee", "amt_full_attendance",
                "ALTER TABLE pay_employee ADD COLUMN amt_full_attendance DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '全勤工资' AFTER amt_post");
        ensureColumn("pay_employee", "amt_social_subsidy",
                "ALTER TABLE pay_employee ADD COLUMN amt_social_subsidy DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '社会保险补贴' AFTER amt_full_attendance");
        ensureColumn("pay_employee", "amt_housing_subsidy",
                "ALTER TABLE pay_employee ADD COLUMN amt_housing_subsidy DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '公积金补贴' AFTER amt_social_subsidy");
        ensureColumn("pay_employee", "amt_transport",
                "ALTER TABLE pay_employee ADD COLUMN amt_transport DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '交通通讯补助' AFTER amt_housing_subsidy");
        ensureColumn("pay_employee", "amt_noncompete",
                "ALTER TABLE pay_employee ADD COLUMN amt_noncompete DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '竞业费' AFTER amt_transport");
        ensureColumn("pay_employee", "amt_hygiene",
                "ALTER TABLE pay_employee ADD COLUMN amt_hygiene DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '洗理费' AFTER amt_noncompete");
        ensureColumn("pay_employee", "amt_fixed_overtime",
                "ALTER TABLE pay_employee ADD COLUMN amt_fixed_overtime DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '固定加班费' AFTER amt_hygiene");
        // 历史只有工资标准、构成全 0 的，先全部记到岗位工资，便于后续拆分
        jdbc.update("""
                UPDATE pay_employee
                SET amt_post = salary_standard
                WHERE is_active = 1
                  AND salary_standard > 0
                  AND amt_post = 0 AND amt_full_attendance = 0 AND amt_social_subsidy = 0
                  AND amt_housing_subsidy = 0 AND amt_transport = 0 AND amt_noncompete = 0
                  AND amt_hygiene = 0 AND amt_fixed_overtime = 0
                """);
    }

    private void widenRewardKind() {
        Integer len = jdbc.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_reward' AND COLUMN_NAME = 'reward_kind'
                """, Integer.class);
        if (len != null && len < 20) {
            jdbc.execute("""
                    ALTER TABLE pay_reward
                    MODIFY COLUMN reward_kind VARCHAR(20) NOT NULL
                    COMMENT '口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过'
                    """);
        }
    }

    private void createAttendanceSummaryTable() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度考勤汇总'
                """);
    }

    private void createRewardTable() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工月度行政通报奖惩'
                """);
    }

    private void createPullTables() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉考勤打卡结果'
                """);
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉行政通报'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_dingtalk_pull (
                  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
                  pull_type    VARCHAR(20) NOT NULL                COMMENT 'attendance/bulletin',
                  period       CHAR(7)     NOT NULL                COMMENT '核算月份',
                  record_count INT         NOT NULL DEFAULT 0      COMMENT '条数',
                  synced_at    DATETIME    NOT NULL                COMMENT '同步时间',
                  synced_by    VARCHAR(50) NOT NULL DEFAULT ''     COMMENT '操作人',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_pull (pull_type, period)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉考勤与通报同步记录'
                """);
    }

    private void createWorkCalendar() {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计薪天数'
                """);
        Integer seeded = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pay_work_calendar WHERE cal_year = 2026", Integer.class);
        if (seeded != null && seeded > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO pay_work_calendar
                  (cal_year, cal_month, calendar_days, holiday_name, holiday_days,
                   dual_rest, dual_work, dual_pay, single_rest, single_work, single_pay,
                   alt_rest, alt_work, alt_pay)
                VALUES
                (2026, 1, 31, '元旦', 1, 10, 21, 22, 5, 26, 27, 8, 23, 24),
                (2026, 2, 28, '春节', 4, 12, 16, 20, 8, 20, 24, 10, 18, 22),
                (2026, 3, 31, '', 0, 9, 22, 22, 5, 26, 26, 7, 24, 24),
                (2026, 4, 30, '清明节', 1, 9, 21, 22, 4, 26, 27, 6, 24, 25),
                (2026, 5, 31, '劳动节', 2, 12, 19, 21, 7, 24, 26, 9, 22, 24),
                (2026, 6, 30, '端午节', 1, 9, 21, 22, 5, 25, 26, 7, 23, 24),
                (2026, 7, 31, '', 0, 8, 23, 23, 4, 27, 27, 6, 25, 25),
                (2026, 8, 31, '', 0, 10, 21, 21, 5, 26, 26, 8, 23, 23),
                (2026, 9, 30, '中秋节', 1, 8, 22, 23, 5, 25, 26, 7, 23, 24),
                (2026, 10, 31, '国庆节', 3, 13, 18, 21, 7, 24, 27, 9, 22, 25),
                (2026, 11, 30, '', 0, 9, 21, 21, 5, 25, 25, 7, 23, 23),
                (2026, 12, 31, '', 0, 8, 23, 23, 4, 27, 27, 6, 25, 25)
                """);
    }

    private void createSocialTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_social_rule (
                  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                  rule_code    VARCHAR(32)   NOT NULL                COMMENT '规则编码',
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社保缴纳规则'
                """);
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社保缴纳规则险种'
                """);
        ensureColumn("pay_social_rule_item", "base_min",
                "ALTER TABLE pay_social_rule_item ADD COLUMN base_min DECIMAL(12,2) NULL COMMENT '本险种基数下限，空则用社保基数' AFTER personal_rate");
        ensureColumn("pay_social_rule_item", "base_max",
                "ALTER TABLE pay_social_rule_item ADD COLUMN base_max DECIMAL(12,2) NULL COMMENT '本险种基数上限，空则用社保基数' AFTER base_min");
    }

    private void seedSocialRules() {
        seedSocialRule("sh_urban", "上海", "城镇", "7546.00", "37731.00", 1,
                "适用2026年7月至2027年6月。在职职工不因户口区分比例，与农村相同。工伤默认一类行业0.2%，实际费率0.2%–1.9%。医疗含生育。",
                items("pension", "养老保险", "16", "8", 1,
                        "medical", "医疗保险（含生育）", "9", "2", 2,
                        "unemployment", "失业保险", "0.50", "0.50", 3,
                        "injury", "工伤保险", "0.20", "0", 4));
        seedSocialRule("sh_rural", "上海", "农村", "7546.00", "37731.00", 2,
                "适用2026年7月至2027年6月。上海在职职工城镇与农村缴费比例相同。工伤默认一类行业0.2%。医疗含生育。",
                items("pension", "养老保险", "16", "8", 1,
                        "medical", "医疗保险（含生育）", "9", "2", 2,
                        "unemployment", "失业保险", "0.50", "0.50", 3,
                        "injury", "工伤保险", "0.20", "0", 4));
        seedSocialRule("xj_urban", "新疆", "城镇", "5246.00", "26231.00", 3,
                "2026年职工养老、失业、工伤基数5246–26231元。医疗、失业、工伤、生育按乌鲁木齐现行职工标准，工伤默认一类0.2%。",
                items("pension", "养老保险", "16", "8", 1,
                        "medical", "医疗保险", "9", "2", 2,
                        "unemployment", "失业保险", "0.50", "0.50", 3,
                        "injury", "工伤保险", "0.20", "0", 4,
                        "maternity", "生育保险", "0.80", "0", 5));
        seedSocialRule("xj_rural", "新疆", "农村", "5246.00", "26231.00", 4,
                "与新疆城镇职工相同，但失业保险个人不缴。农民合同工由单位缴纳失业保险0.5%，个人不扣。",
                items("pension", "养老保险", "16", "8", 1,
                        "medical", "医疗保险", "9", "2", 2,
                        "unemployment", "失业保险", "0.50", "0", 3,
                        "injury", "工伤保险", "0.20", "0", 4,
                        "maternity", "生育保险", "0.80", "0", 5));
        ensureHousingFund("sh_urban", "7", "7", "2740.00", "37731.00",
                "住房公积金基数2740–37731元，单位和个人各5%–7%，默认各7%。补充公积金自愿，各1%–5%。");
        ensureHousingFund("sh_rural", "7", "7", "2740.00", "37731.00",
                "住房公积金基数2740–37731元，单位和个人各5%–7%，默认各7%。城镇与农村职工相同。");
        ensureHousingFund("xj_urban", "12", "12", "2070.00", "35071.00",
                "住房公积金基数2070–35071元，单位和个人各5%–12%且比例相同，默认各12%。");
        ensureHousingFund("xj_rural", "12", "12", "2070.00", "35071.00",
                "住房公积金与新疆城镇职工相同：基数2070–35071元，默认各12%。");
    }

    private void ensureHousingFund(String ruleCode, String employer, String personal, String baseMin, String baseMax, String note) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM pay_social_rule WHERE rule_code = ? AND is_active = 1",
                (rs, row) -> rs.getLong(1), ruleCode);
        if (ids.isEmpty()) {
            return;
        }
        Long ruleId = ids.get(0);
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_social_rule_item
                WHERE rule_id = ? AND item_code = 'housing' AND is_active = 1
                """, Integer.class, ruleId);
        if (exists == null || exists == 0) {
            jdbc.update("""
                    INSERT INTO pay_social_rule_item
                      (rule_id, item_code, item_name, employer_rate, personal_rate, base_min, base_max, sort_order, is_active)
                    VALUES (?, 'housing', '住房公积金', ?, ?, ?, ?, 6, 1)
                    """, ruleId, new java.math.BigDecimal(employer), new java.math.BigDecimal(personal),
                    new java.math.BigDecimal(baseMin), new java.math.BigDecimal(baseMax));
        }
        jdbc.update("""
                UPDATE pay_social_rule
                SET remark = CONCAT(remark, ' ', ?)
                WHERE id = ? AND remark NOT LIKE '%公积金%'
                """, note, ruleId);
    }

    private void seedSocialRule(String code, String region, String hukou, String baseMin, String baseMax, int sort,
                                String remark, Object[][] ruleItems) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pay_social_rule WHERE rule_code = ?", Integer.class, code);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO pay_social_rule
                  (rule_code, region_name, hukou_type, base_min, base_max, remark, sort_order, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'system', 1)
                """, code, region, hukou, new java.math.BigDecimal(baseMin), new java.math.BigDecimal(baseMax), remark, sort);
        Long ruleId = jdbc.queryForObject("SELECT id FROM pay_social_rule WHERE rule_code = ?", Long.class, code);
        for (Object[] item : ruleItems) {
            jdbc.update("""
                    INSERT INTO pay_social_rule_item
                      (rule_id, item_code, item_name, employer_rate, personal_rate, sort_order, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                    """, ruleId, item[0], item[1], new java.math.BigDecimal((String) item[2]),
                    new java.math.BigDecimal((String) item[3]), item[4]);
        }
    }

    private static Object[][] items(Object... values) {
        Object[][] rows = new Object[values.length / 5][5];
        for (int i = 0; i < rows.length; i++) {
            System.arraycopy(values, i * 5, rows[i], 0, 5);
        }
        return rows;
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute(ddl);
    }

    private void ensureIndex(String table, String indexName, String ddl) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, table, indexName);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute(ddl);
    }
}
