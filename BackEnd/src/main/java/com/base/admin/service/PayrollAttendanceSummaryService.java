package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayAttendanceSummaryQueryDTO;
import com.base.admin.domain.vo.PayAttendanceSummaryVO;
import com.base.admin.service.PayrollDingTalkPullService.PeriodWindow;
import com.base.admin.service.PayrollDingTalkPullService.Staff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollAttendanceSummaryService {

    private static final String LEAVE_NAMES = "年假,调休,婚假,丧假,产假,陪产假,育儿假";

    private final JdbcTemplate jdbc;
    private final DingTalkAuthService dingTalkAuthService;
    private final PayrollDingTalkPullService pullService;

    public PageResult<PayAttendanceSummaryVO> page(PayAttendanceSummaryQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getPeriod())) {
            where.append(" AND period = ?");
            args.add(pullService.parsePeriod(query.getPeriod()).period());
        }
        if (StringUtils.hasText(query.getEmployeeName())) {
            where.append(" AND employee_name LIKE ?");
            args.add("%" + query.getEmployeeName().trim() + "%");
        }
        if (StringUtils.hasText(query.getDeptName())) {
            where.append(" AND dept_name LIKE ?");
            args.add("%" + query.getDeptName().trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_attendance_summary" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayAttendanceSummaryVO> rows = jdbc.query("""
                SELECT id, period, employee_id, employee_name, dept_name, late_early_count, personal1_count, personal2_count,
                       absent_count, missing_card_count, annual_leave_days, special_leave_days, actual_rest_days, remaining_comp_days
                FROM pay_attendance_summary
                """ + where + " ORDER BY employee_name, id LIMIT ? OFFSET ?", (rs, row) -> {
            PayAttendanceSummaryVO vo = new PayAttendanceSummaryVO();
            vo.setId(rs.getLong("id"));
            vo.setPeriod(rs.getString("period"));
            vo.setEmployeeId(rs.getLong("employee_id"));
            vo.setEmployeeName(rs.getString("employee_name"));
            vo.setDeptName(rs.getString("dept_name"));
            vo.setLateEarlyCount(rs.getInt("late_early_count"));
            vo.setPersonal1Count(rs.getInt("personal1_count"));
            vo.setPersonal2Count(rs.getInt("personal2_count"));
            vo.setAbsentCount(rs.getInt("absent_count"));
            vo.setMissingCardCount(rs.getInt("missing_card_count"));
            vo.setAnnualLeaveDays(rs.getBigDecimal("annual_leave_days"));
            vo.setSpecialLeaveDays(rs.getBigDecimal("special_leave_days"));
            vo.setActualRestDays(rs.getBigDecimal("actual_rest_days"));
            vo.setRemainingCompDays(rs.getBigDecimal("remaining_comp_days"));
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    /**
     * 按打卡结果汇总迟到早退/缺卡/旷工，并按职级规则折算事假1/2；再尽量拉取年假、婚丧产、调休。
     */
    public int rebuild(String period) {
        PeriodWindow window = pullService.parsePeriod(period);
        Map<String, Staff> staff = pullService.staffByDingId();
        Map<String, Agg> aggs = new HashMap<>();
        for (Map.Entry<String, Staff> entry : staff.entrySet()) {
            Agg agg = new Agg();
            agg.dingId = entry.getKey();
            agg.name = entry.getValue().name();
            agg.dept = entry.getValue().deptName();
            aggs.put(entry.getKey(), agg);
        }
        List<Map<String, Object>> punches = jdbc.queryForList("""
                SELECT dingtalk_user_id, employee_name, dept_name, time_result
                FROM pay_attendance WHERE period = ?
                """, window.period());
        for (Map<String, Object> punch : punches) {
            String dingId = String.valueOf(punch.get("dingtalk_user_id"));
            Agg agg = aggs.computeIfAbsent(dingId, id -> {
                Agg created = new Agg();
                created.dingId = id;
                created.name = String.valueOf(punch.getOrDefault("employee_name", ""));
                created.dept = String.valueOf(punch.getOrDefault("dept_name", ""));
                return created;
            });
            String result = String.valueOf(punch.getOrDefault("time_result", ""));
            if ("Late".equals(result) || "SeriousLate".equals(result) || "Early".equals(result)) {
                agg.lateEarly++;
            } else if ("Absenteeism".equals(result)) {
                agg.absent++;
            } else if ("NotSigned".equals(result)) {
                agg.missing++;
            }
        }
        Map<String, Long> employeeIds = employeeIdByDing();
        Map<Long, LateRule> rules = lateRulesByGrade();
        Map<Long, Long> gradeByEmployee = gradeByEmployee();
        for (Agg agg : aggs.values()) {
            Long empId = employeeIds.getOrDefault(agg.dingId, 0L);
            Long gradeId = gradeByEmployee.getOrDefault(empId, 0L);
            LateRule rule = rules.get(gradeId);
            int allow = rule == null ? 3 : rule.allowCount;
            if (agg.lateEarly <= 0) {
                agg.personal1 = 0;
                agg.personal2 = 0;
            } else if (agg.lateEarly <= allow) {
                agg.personal1 = 1;
                agg.personal2 = 0;
            } else {
                agg.personal1 = 1;
                agg.personal2 = agg.lateEarly - allow;
            }
        }
        enrichLeave(aggs, window);
        jdbc.update("DELETE FROM pay_attendance_summary WHERE period = ?", window.period());
        int count = 0;
        for (Agg agg : aggs.values()) {
            Long empId = employeeIds.getOrDefault(agg.dingId, 0L);
            jdbc.update("""
                    INSERT INTO pay_attendance_summary
                      (period, employee_id, dingtalk_user_id, employee_name, dept_name,
                       late_early_count, personal1_count, personal2_count, absent_count, missing_card_count,
                       annual_leave_days, special_leave_days, actual_rest_days, remaining_comp_days)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, window.period(), empId, agg.dingId, agg.name, agg.dept,
                    agg.lateEarly, agg.personal1, agg.personal2, agg.absent, agg.missing,
                    agg.annual, agg.special, agg.actualRest, agg.remainingComp);
            count++;
        }
        return count;
    }

    private void enrichLeave(Map<String, Agg> aggs, PeriodWindow window) {
        String token;
        try {
            token = dingTalkAuthService.requireAppAccessToken();
        } catch (Exception ex) {
            log.warn("考勤汇总拉取假期时获取 token 失败: {}", ex.getMessage());
            return;
        }
        String opUser = firstOperator();
        String leaveCode = StringUtils.hasText(opUser) ? findCompLeaveCode(token, opUser) : null;
        int i = 0;
        for (Agg agg : aggs.values()) {
            try {
                fillLeave(token, agg, window, leaveCode, opUser);
            } catch (Exception ex) {
                log.debug("拉取 {} 假期数据失败: {}", agg.dingId, ex.getMessage());
            }
            if (++i % 20 == 0) {
                pause();
            }
        }
    }

    private void fillLeave(String token, Agg agg, PeriodWindow window, String leaveCode, String opUser) {
        Map<String, BigDecimal> days = leaveDaysByName(token, agg.dingId, window);
        agg.annual = days.getOrDefault("年假", BigDecimal.ZERO);
        BigDecimal special = BigDecimal.ZERO;
        for (String name : List.of("婚假", "丧假", "产假", "陪产假", "育儿假")) {
            special = special.add(days.getOrDefault(name, BigDecimal.ZERO));
        }
        agg.special = special;
        agg.actualRest = days.getOrDefault("调休", BigDecimal.ZERO);
        if (StringUtils.hasText(leaveCode) && StringUtils.hasText(opUser)) {
            agg.remainingComp = remainingComp(token, leaveCode, opUser, agg.dingId);
        }
    }

    private Map<String, BigDecimal> leaveDaysByName(String token, String userId, PeriodWindow window) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("userid", userId);
        body.put("leave_names", LEAVE_NAMES);
        body.put("from_date", window.start() + " 00:00:00");
        body.put("to_date", window.end() + " 23:59:59");
        JsonNode json = dingTalkAuthService.postOapi(token, "topapi/attendance/getleavetimebynames", body,
                "钉钉假期权限不足：请开通假期/考勤报表相关读权限");
        Map<String, BigDecimal> map = new HashMap<>();
        JsonNode columns = json.path("result").path("columns");
        if (!columns.isArray()) {
            columns = json.path("result");
        }
        if (!columns.isArray()) {
            return map;
        }
        for (JsonNode col : columns) {
            String name = first(col, "columnvo", "columnname", "column_name", "leave_name");
            if (!StringUtils.hasText(name)) {
                JsonNode vo = col.path("columnvo");
                if (vo.isObject()) {
                    name = first(vo, "name", "alias_name", "column_name");
                }
            }
            if (!StringUtils.hasText(name)) {
                continue;
            }
            BigDecimal total = BigDecimal.ZERO;
            JsonNode values = col.path("columnvals");
            if (!values.isArray()) {
                values = col.path("column_vals");
            }
            if (values.isArray()) {
                for (JsonNode val : values) {
                    String raw = first(val, "value", "val");
                    if (StringUtils.hasText(raw)) {
                        try {
                            total = total.add(new BigDecimal(raw.trim()));
                        } catch (NumberFormatException ignored) {
                            // ignore non-numeric cells
                        }
                    }
                }
            }
            map.put(name, total.setScale(2, RoundingMode.HALF_UP));
        }
        return map;
    }

    private BigDecimal remainingComp(String token, String leaveCode, String opUser, String userId) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("leave_code", leaveCode);
        body.put("op_userid", opUser);
        body.put("userids", userId);
        body.put("offset", 0);
        body.put("size", 50);
        JsonNode json = dingTalkAuthService.postOapi(token, "topapi/attendance/vacation/quota/list", body,
                "钉钉假期权限不足：请开通「钉钉假期读权限」");
        JsonNode quotas = json.path("result").path("leave_quotas");
        if (!quotas.isArray()) {
            return BigDecimal.ZERO;
        }
        BigDecimal remain = BigDecimal.ZERO;
        for (JsonNode q : quotas) {
            BigDecimal quotaDay = decimal(q, "quota_num_per_day");
            BigDecimal usedDay = decimal(q, "used_num_per_day");
            if (quotaDay.compareTo(BigDecimal.ZERO) > 0 || usedDay.compareTo(BigDecimal.ZERO) > 0) {
                remain = remain.add(quotaDay.subtract(usedDay).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal quotaHour = decimal(q, "quota_num_per_hour");
            BigDecimal usedHour = decimal(q, "used_num_per_hour");
            remain = remain.add(quotaHour.subtract(usedHour).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return remain.max(BigDecimal.ZERO);
    }

    private String findCompLeaveCode(String token, String opUser) {
        try {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.put("op_userid", opUser);
            body.put("vacation_source", "all");
            JsonNode json = dingTalkAuthService.postOapi(token, "topapi/attendance/vacation/type/list", body,
                    "钉钉假期权限不足：请开通「钉钉假期读权限」");
            JsonNode list = json.path("result");
            if (!list.isArray()) {
                return null;
            }
            for (JsonNode item : list) {
                String name = item.path("leave_name").asText("");
                String biz = item.path("biz_type").asText("");
                if ("调休".equals(name) || "lieu_leave".equals(biz)) {
                    return item.path("leave_code").asText(null);
                }
            }
        } catch (Exception ex) {
            log.warn("查询调休假期规则失败: {}", ex.getMessage());
        }
        return null;
    }

    private String firstOperator() {
        List<String> ids = jdbc.query("""
                SELECT dingtalk_user_id FROM hr_user_dingtalk
                WHERE is_active = 1 AND dingtalk_user_id <> ''
                ORDER BY CASE WHEN user_id = 1 THEN 0 ELSE 1 END, user_id LIMIT 1
                """, (rs, row) -> rs.getString(1));
        return ids.isEmpty() ? "" : ids.get(0);
    }

    private Map<String, Long> employeeIdByDing() {
        Map<String, Long> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, dingtalk_user_id FROM pay_employee
                WHERE is_active = 1 AND dingtalk_user_id <> ''
                """);
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("dingtalk_user_id")), ((Number) row.get("id")).longValue());
        }
        return map;
    }

    private Map<Long, Long> gradeByEmployee() {
        Map<Long, Long> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, grade_id FROM pay_employee WHERE is_active = 1
                """);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("id")).longValue(), ((Number) row.get("grade_id")).longValue());
        }
        return map;
    }

    private Map<Long, LateRule> lateRulesByGrade() {
        Map<Long, LateRule> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT grade_id, allow_count FROM pay_late_rule WHERE is_active = 1
                """);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("grade_id")).longValue(),
                    new LateRule(((Number) row.get("allow_count")).intValue()));
        }
        return map;
    }

    private static void pause() {
        try {
            Thread.sleep(40);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.asText("0"));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.path(field);
            if (child.isObject()) {
                continue;
            }
            String value = child.asText("");
            if (StringUtils.hasText(value) && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class Agg {
        private String dingId;
        private String name = "";
        private String dept = "";
        private int lateEarly;
        private int personal1;
        private int personal2;
        private int absent;
        private int missing;
        private BigDecimal annual = BigDecimal.ZERO;
        private BigDecimal special = BigDecimal.ZERO;
        private BigDecimal actualRest = BigDecimal.ZERO;
        private BigDecimal remainingComp = BigDecimal.ZERO;
    }

    private record LateRule(int allowCount) {
    }
}
