package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayAttendanceQueryDTO;
import com.base.admin.domain.vo.PayAttendanceVO;
import com.base.admin.domain.vo.PayDingTalkPullVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.PayrollDingTalkPullService.PeriodWindow;
import com.base.admin.service.PayrollDingTalkPullService.Staff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollAttendanceSyncService {

    private static final String PERMISSION = "钉钉考勤权限不足：请在开放平台为该应用开通「考勤数据读权限」。";
    private static final int USER_BATCH = 50;
    private static final int PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final DingTalkAuthService dingTalkAuthService;
    private final PayrollDingTalkPullService pullService;
    private final PayrollAttendanceSummaryService summaryService;
    private final PlatformTransactionManager transactionManager;

    public PayDingTalkPullVO sync(String period) {
        PeriodWindow window = pullService.parsePeriod(period);
        Map<String, Staff> staff = pullService.staffByDingId();
        if (staff.isEmpty()) {
            throw new BusinessException("还没有钉钉员工，请先在员工管理里同步通讯录");
        }
        String token = dingTalkAuthService.requireAppAccessToken();
        List<String> userIds = new ArrayList<>(staff.keySet());
        Map<String, Row> rows = new LinkedHashMap<>();
        LocalDate cursor = window.start();
        while (!cursor.isAfter(window.end())) {
            LocalDate windowEnd = cursor.plusDays(6);
            if (windowEnd.isAfter(window.end())) {
                windowEnd = window.end();
            }
            for (int i = 0; i < userIds.size(); i += USER_BATCH) {
                List<String> batch = userIds.subList(i, Math.min(i + USER_BATCH, userIds.size()));
                fetchBatch(token, batch, cursor, windowEnd, rows);
            }
            cursor = windowEnd.plusDays(1);
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            replace(window.period(), new ArrayList<>(rows.values()), staff);
            pullService.mark("attendance", window.period(), rows.size());
        });
        int summaryCount = summaryService.rebuild(window.period());
        PayDingTalkPullVO vo = new PayDingTalkPullVO();
        vo.setPeriod(window.period());
        vo.setRecordCount(rows.size());
        vo.setMessage("已同步 " + window.period() + " 考勤 " + rows.size()
                + " 条，并汇总 " + summaryCount + " 人。核算该月工资前请同时同步行政通报。");
        return vo;
    }

    public PageResult<PayAttendanceVO> page(PayAttendanceQueryDTO query) {
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
        if (StringUtils.hasText(query.getTimeResult())) {
            where.append(" AND time_result = ?");
            args.add(query.getTimeResult().trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_attendance" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayAttendanceVO> rows = jdbc.query("""
                SELECT id, period, employee_name, dept_name, work_date, check_type, time_result,
                       DATE_FORMAT(user_check_time, '%Y-%m-%d %H:%i:%s') AS user_check_time,
                       DATE_FORMAT(base_check_time, '%Y-%m-%d %H:%i:%s') AS base_check_time
                FROM pay_attendance
                """ + where + " ORDER BY work_date DESC, employee_name, check_type LIMIT ? OFFSET ?", (rs, row) -> {
            PayAttendanceVO vo = new PayAttendanceVO();
            vo.setId(rs.getLong("id"));
            vo.setPeriod(rs.getString("period"));
            vo.setEmployeeName(rs.getString("employee_name"));
            vo.setDeptName(rs.getString("dept_name"));
            vo.setWorkDate(rs.getString("work_date"));
            vo.setCheckType(rs.getString("check_type"));
            vo.setTimeResult(rs.getString("time_result"));
            vo.setUserCheckTime(rs.getString("user_check_time"));
            vo.setBaseCheckTime(rs.getString("base_check_time"));
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    private void fetchBatch(String token, List<String> userIds, LocalDate from, LocalDate to, Map<String, Row> rows) {
        int offset = 0;
        for (int page = 0; page < 40; page++) {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            ArrayNode ids = body.putArray("userIdList");
            userIds.forEach(ids::add);
            body.put("workDateFrom", from + " 00:00:00");
            body.put("workDateTo", to + " 00:00:00");
            body.put("offset", offset);
            body.put("limit", PAGE_SIZE);
            JsonNode json = dingTalkAuthService.postOapi(token, "attendance/list", body, PERMISSION);
            JsonNode list = json.path("recordresult");
            if (!list.isArray()) {
                list = json.path("result").path("recordresult");
            }
            if (list.isArray()) {
                for (JsonNode item : list) {
                    Row row = toRow(item);
                    if (row != null) {
                        rows.put(row.sourceId, row);
                    }
                }
            }
            boolean more = json.path("hasMore").asBoolean(false) || json.path("result").path("hasMore").asBoolean(false);
            if (!more) {
                return;
            }
            offset += PAGE_SIZE;
            pause();
        }
    }

    private Row toRow(JsonNode item) {
        String userId = text(item, "userId");
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        LocalDate workDate = dateOf(item.path("workDate").asLong(0));
        if (workDate == null) {
            return null;
        }
        String checkType = text(item, "checkType");
        String sourceId = firstText(item, "id", "recordId");
        if (!StringUtils.hasText(sourceId)) {
            sourceId = userId + "|" + workDate + "|" + checkType;
        }
        Row row = new Row();
        row.userId = userId;
        row.workDate = workDate;
        row.checkType = cut(checkType, 20);
        row.timeResult = cut(text(item, "timeResult"), 32);
        row.locationResult = cut(text(item, "locationResult"), 20);
        row.userCheckTime = timeOf(item.path("userCheckTime").asLong(0));
        row.baseCheckTime = timeOf(item.path("baseCheckTime").asLong(0));
        row.sourceId = cut(sourceId, 64);
        return row;
    }

    private void replace(String period, List<Row> rows, Map<String, Staff> staff) {
        jdbc.update("DELETE FROM pay_attendance WHERE period = ?", period);
        for (int from = 0; from < rows.size(); from += 400) {
            List<Row> chunk = rows.subList(from, Math.min(from + 400, rows.size()));
            jdbc.batchUpdate("""
                    INSERT INTO pay_attendance
                      (period, dingtalk_user_id, employee_name, dept_name, work_date, check_type, time_result,
                       location_result, user_check_time, base_check_time, source_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Row row = chunk.get(i);
                    Staff person = staff.get(row.userId);
                    ps.setString(1, period);
                    ps.setString(2, row.userId);
                    ps.setString(3, cut(person == null ? "未匹配员工" : person.name(), 50));
                    ps.setString(4, cut(person == null ? "" : person.deptName(), 50));
                    ps.setDate(5, java.sql.Date.valueOf(row.workDate));
                    ps.setString(6, row.checkType);
                    ps.setString(7, row.timeResult);
                    ps.setString(8, row.locationResult);
                    setTime(ps, 9, row.userCheckTime);
                    setTime(ps, 10, row.baseCheckTime);
                    ps.setString(11, row.sourceId);
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private static void setTime(PreparedStatement ps, int index, LocalDateTime time) throws SQLException {
        if (time == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(time));
        }
    }

    private static LocalDate dateOf(long millis) {
        if (millis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(millis).atZone(PayrollDingTalkPullService.SHANGHAI).toLocalDate();
    }

    private static LocalDateTime timeOf(long millis) {
        if (millis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(millis).atZone(PayrollDingTalkPullService.SHANGHAI).toLocalDateTime();
    }

    private static void pause() {
        try {
            Thread.sleep(40);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("考勤同步已中断");
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value) && !"0".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static final class Row {
        private String userId;
        private LocalDate workDate;
        private String checkType;
        private String timeResult;
        private String locationResult;
        private LocalDateTime userCheckTime;
        private LocalDateTime baseCheckTime;
        private String sourceId;
    }
}
