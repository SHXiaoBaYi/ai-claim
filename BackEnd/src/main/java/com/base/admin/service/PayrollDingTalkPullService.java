package com.base.admin.service;

import com.base.admin.domain.vo.PayMonthPullStatusVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollDingTalkPullService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public PayMonthPullStatusVO status(String period) {
        String month = parsePeriod(period).period();
        PayMonthPullStatusVO vo = new PayMonthPullStatusVO();
        vo.setPeriod(month);
        vo.setAttendance(item("attendance", month));
        vo.setBulletin(item("bulletin", month));
        return vo;
    }

    public void mark(String pullType, String period, int count) {
        String user = SecurityUtils.getCurrentUsername();
        jdbc.update("""
                INSERT INTO pay_dingtalk_pull (pull_type, period, record_count, synced_at, synced_by)
                VALUES (?, ?, ?, NOW(), ?)
                ON DUPLICATE KEY UPDATE record_count = VALUES(record_count), synced_at = NOW(), synced_by = VALUES(synced_by)
                """, pullType, period, count, StringUtils.hasText(user) ? user : "");
    }

    public Map<String, Staff> staffByDingId() {
        Map<String, Staff> staff = new LinkedHashMap<>();
        jdbc.query("""
                SELECT e.dingtalk_user_id, e.employee_name, IFNULL(d.dept_name, '') AS dept_name
                FROM pay_employee e
                LEFT JOIN pay_dept d ON d.id = e.dept_id
                WHERE e.is_active = 1 AND e.dingtalk_user_id <> ''
                """, rs -> {
            staff.put(rs.getString("dingtalk_user_id"), new Staff(rs.getString("employee_name"), rs.getString("dept_name")));
        });
        return staff;
    }

    public PeriodWindow parsePeriod(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("请选择月份");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("月份格式应为 yyyy-MM");
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        if (month.atDay(1).isAfter(today)) {
            throw new BusinessException("不能同步未来月份");
        }
        if (month.atDay(1).isBefore(today.minusDays(180))) {
            throw new BusinessException("钉钉不提供半年以前的记录，请选近半年的月份");
        }
        LocalDate end = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        return new PeriodWindow(month.toString(), month.atDay(1), end);
    }

    private PayMonthPullStatusVO.Item item(String type, String period) {
        PayMonthPullStatusVO.Item item = new PayMonthPullStatusVO.Item();
        jdbc.query("""
                SELECT record_count, DATE_FORMAT(synced_at, '%Y-%m-%d %H:%i:%s') AS synced_at
                FROM pay_dingtalk_pull
                WHERE pull_type = ? AND period = ?
                """, rs -> {
            item.setRecordCount(rs.getInt("record_count"));
            item.setSyncedAt(rs.getString("synced_at"));
        }, type, period);
        return item;
    }

    public record PeriodWindow(String period, LocalDate start, LocalDate end) {
    }

    public record Staff(String name, String deptName) {
    }
}
