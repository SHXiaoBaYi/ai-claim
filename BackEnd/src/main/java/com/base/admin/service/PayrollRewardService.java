package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.common.PayRewardKinds;
import com.base.admin.domain.dto.PayRewardDTO;
import com.base.admin.domain.dto.PayRewardQueryDTO;
import com.base.admin.domain.vo.PayRewardVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PayrollRewardService {

    private static final Pattern EXPLICIT_AMOUNT = Pattern.compile(
            "(?:奖励|嘉奖|表彰|奖金|奖|罚款|处罚|扣款|扣罚|罚金|罚)[^0-9\\-]{0,12}(-?\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern LOOSE_AMOUNT = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern TIMES = Pattern.compile("(\\d+)\\s*次");

    private final JdbcTemplate jdbc;

    public PageResult<PayRewardVO> page(PayRewardQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE r.is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getPeriod())) {
            where.append(" AND r.period = ?");
            args.add(normalizePeriod(query.getPeriod()));
        }
        if (StringUtils.hasText(query.getEmployeeName())) {
            where.append(" AND e.employee_name LIKE ?");
            args.add("%" + query.getEmployeeName().trim() + "%");
        }
        if (query.getDeptId() != null) {
            where.append(" AND e.dept_id = ?");
            args.add(query.getDeptId());
        }
        if (StringUtils.hasText(query.getRewardKind())) {
            where.append(" AND r.reward_kind = ?");
            args.add(query.getRewardKind().trim());
        }
        String from = """
                FROM pay_reward r
                INNER JOIN pay_employee e ON e.id = r.employee_id AND e.is_active = 1
                LEFT JOIN pay_dept d ON d.id = e.dept_id AND d.is_active = 1
                LEFT JOIN pay_grade g ON g.id = e.grade_id AND g.is_active = 1
                LEFT JOIN pay_bulletin b ON b.id = r.bulletin_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(1) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayRewardVO> rows = jdbc.query("""
                SELECT r.id, r.period, r.employee_id, e.employee_name, d.dept_name, g.grade_name,
                       r.bulletin_id, b.title AS bulletin_title, r.reward_kind, r.reason, r.times,
                       r.unit_amount, r.amount, r.amount_source, r.remark
                """ + from + where + " ORDER BY r.period DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    PayRewardVO vo = new PayRewardVO();
                    vo.setId(rs.getLong("id"));
                    vo.setPeriod(rs.getString("period"));
                    vo.setEmployeeId(rs.getLong("employee_id"));
                    vo.setEmployeeName(rs.getString("employee_name"));
                    vo.setDeptName(rs.getString("dept_name"));
                    vo.setGradeName(rs.getString("grade_name"));
                    long bulletinId = rs.getLong("bulletin_id");
                    vo.setBulletinId(rs.wasNull() ? null : bulletinId);
                    vo.setBulletinTitle(rs.getString("bulletin_title"));
                    vo.setRewardKind(rs.getString("reward_kind"));
                    vo.setReason(rs.getString("reason"));
                    vo.setTimes(rs.getInt("times"));
                    vo.setUnitAmount(rs.getBigDecimal("unit_amount"));
                    vo.setAmount(rs.getBigDecimal("amount"));
                    vo.setAmountSource(rs.getString("amount_source"));
                    vo.setRemark(rs.getString("remark"));
                    return vo;
                }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void save(PayRewardDTO dto) {
        String period = normalizePeriod(dto.getPeriod());
        assertEmployee(dto.getEmployeeId());
        String kind = dto.getRewardKind().trim();
        if (!PayRewardKinds.isKnown(kind)) {
            throw new BusinessException("奖惩类型不正确");
        }
        int times = dto.getTimes() == null ? 1 : dto.getTimes();
        BigDecimal abs = dto.getAmount().abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal signed = PayRewardKinds.isPunish(kind) ? abs.negate() : abs;
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        String user = currentUser();
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_reward
                      (period, employee_id, bulletin_id, blackboard_id, reward_kind, reason, times,
                       unit_amount, amount, amount_source, remark, create_by, is_active)
                    VALUES (?, ?, NULL, '', ?, ?, ?, NULL, ?, 'manual', ?, ?, 1)
                    """, period, dto.getEmployeeId(), kind, reason, times, signed, remark, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_reward
                SET period = ?, employee_id = ?, reward_kind = ?, reason = ?, times = ?,
                    unit_amount = NULL, amount = ?, amount_source = 'manual', remark = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, period, dto.getEmployeeId(), kind, reason, times, signed, remark, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("奖惩记录不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        int rows = jdbc.update("""
                UPDATE pay_reward SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1
                """, currentUser(), id);
        if (rows == 0) {
            throw new BusinessException("奖惩记录不存在");
        }
    }

    /**
     * 同步行政通报后重建该月自动奖惩：保留手工记录，按通报收件人 + 职级单价/明文金额生成。
     */
    @Transactional
    public int rebuildFromBulletins(String period, List<SyncedBulletin> bulletins) {
        String month = normalizePeriod(period);
        jdbc.update("""
                DELETE FROM pay_reward
                WHERE period = ? AND amount_source IN ('bulletin', 'grade')
                """, month);
        Map<String, Staff> staffByName = staffByName();
        Map<Long, Map<String, BigDecimal>> units = gradeUnits();
        String user = currentUser();
        int created = 0;
        for (SyncedBulletin bulletin : bulletins) {
            String kind = mapKind(bulletin.kind(), bulletin.title() + "\n" + bulletin.plain());
            if (kind == null) {
                continue;
            }
            Parsed parsed = parse(bulletin.title() + "\n" + bulletin.plain(), kind);
            List<Staff> people = matchPeople(bulletin.receiver(), staffByName);
            if (people.isEmpty()) {
                continue;
            }
            String reason = cut(StringUtils.hasText(bulletin.title()) ? bulletin.title() : parsed.reasonHint, 500);
            for (Staff staff : people) {
                Map<String, BigDecimal> unitMap = units.getOrDefault(staff.gradeId, Map.of());
                BigDecimal unitAmount = unitMap.getOrDefault(kind, BigDecimal.ZERO);
                BigDecimal amount;
                String source;
                if (parsed.explicitAmount != null) {
                    amount = parsed.explicitAmount.abs().setScale(2, RoundingMode.HALF_UP);
                    if (PayRewardKinds.isPunish(kind)) {
                        amount = amount.negate();
                    }
                    source = "bulletin";
                } else {
                    amount = unitAmount.multiply(BigDecimal.valueOf(parsed.times)).setScale(2, RoundingMode.HALF_UP);
                    if (PayRewardKinds.isPunish(kind)) {
                        amount = amount.negate();
                    }
                    source = "grade";
                }
                jdbc.update("""
                        INSERT INTO pay_reward
                          (period, employee_id, bulletin_id, blackboard_id, reward_kind, reason, times,
                           unit_amount, amount, amount_source, remark, create_by, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, 1)
                        """, month, staff.id, bulletin.id(), nullToEmpty(bulletin.blackboardId()), kind, reason,
                        parsed.times, unitAmount, amount, source, user);
                created++;
            }
        }
        return created;
    }

    public long insertBulletin(String period, String blackboardId, String title, String categoryName, String author,
                               String kind, String plain, LocalDateTime publishTime, String receiver) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO pay_bulletin
                      (period, blackboard_id, title, category_name, author, bulletin_kind, plain_content, publish_time, receiver_text)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, period);
            ps.setString(2, blackboardId);
            ps.setString(3, title);
            ps.setString(4, categoryName);
            ps.setString(5, author);
            ps.setString(6, kind);
            ps.setString(7, plain);
            if (publishTime == null) {
                ps.setTimestamp(8, null);
            } else {
                ps.setTimestamp(8, Timestamp.valueOf(publishTime));
            }
            ps.setString(9, receiver);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new BusinessException("保存行政通报失败");
        }
        return key.longValue();
    }

    static Parsed parse(String text, String kind) {
        String raw = text == null ? "" : text;
        BigDecimal explicit = null;
        Matcher m = EXPLICIT_AMOUNT.matcher(raw);
        if (m.find()) {
            explicit = new BigDecimal(m.group(1));
        } else if (raw.contains("元")) {
            Matcher loose = LOOSE_AMOUNT.matcher(raw);
            if (loose.find()) {
                explicit = new BigDecimal(loose.group(1));
            }
        }
        int times = 1;
        Matcher tm = TIMES.matcher(raw);
        if (tm.find()) {
            times = Math.max(1, Integer.parseInt(tm.group(1)));
        }
        return new Parsed(explicit, times, cut(raw.replaceAll("\\s+", " ").trim(), 80));
    }

    private static String mapKind(String bulletinKind, String text) {
        if (PayRewardKinds.isKnown(bulletinKind)) {
            return bulletinKind;
        }
        return PayRewardKinds.classify(text);
    }

    private List<Staff> matchPeople(String receiver, Map<String, Staff> byName) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Staff> matched = new ArrayList<>();
        if (!StringUtils.hasText(receiver)) {
            return matched;
        }
        for (String part : receiver.split("[、,，;；/｜|]")) {
            String name = part.trim();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Staff staff = byName.get(name);
            if (staff != null && seen.add(staff.id)) {
                matched.add(staff);
            }
        }
        return matched;
    }

    private Map<String, Staff> staffByName() {
        Map<String, Staff> map = new HashMap<>();
        List<StaffName> rows = jdbc.query("""
                SELECT id, employee_name, grade_id
                FROM pay_employee
                WHERE is_active = 1 AND job_status = 1
                """, (rs, row) -> new StaffName(rs.getLong("id"), rs.getString("employee_name"), rs.getLong("grade_id")));
        for (StaffName row : rows) {
            if (!StringUtils.hasText(row.name)) {
                continue;
            }
            map.putIfAbsent(row.name.trim(), new Staff(row.id, row.gradeId));
        }
        return map;
    }

    private Map<Long, Map<String, BigDecimal>> gradeUnits() {
        Map<Long, Map<String, BigDecimal>> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT grade_id, amt_verbal_praise, amt_commendation, amt_merit, amt_major_merit,
                       amt_verbal_critic, amt_bulletin_critic, amt_demerit, amt_major_demerit
                FROM pay_late_rule WHERE is_active = 1
                """);
        for (Map<String, Object> row : rows) {
            Map<String, BigDecimal> amounts = new HashMap<>();
            amounts.put(PayRewardKinds.VERBAL_PRAISE, nz((BigDecimal) row.get("amt_verbal_praise")));
            amounts.put(PayRewardKinds.COMMENDATION, nz((BigDecimal) row.get("amt_commendation")));
            amounts.put(PayRewardKinds.MERIT, nz((BigDecimal) row.get("amt_merit")));
            amounts.put(PayRewardKinds.MAJOR_MERIT, nz((BigDecimal) row.get("amt_major_merit")));
            amounts.put(PayRewardKinds.VERBAL_CRITIC, nz((BigDecimal) row.get("amt_verbal_critic")));
            amounts.put(PayRewardKinds.BULLETIN_CRITIC, nz((BigDecimal) row.get("amt_bulletin_critic")));
            amounts.put(PayRewardKinds.DEMERIT, nz((BigDecimal) row.get("amt_demerit")));
            amounts.put(PayRewardKinds.MAJOR_DEMERIT, nz((BigDecimal) row.get("amt_major_demerit")));
            map.put(((Number) row.get("grade_id")).longValue(), amounts);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void assertEmployee(Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee WHERE id = ? AND is_active = 1
                """, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("员工不存在");
        }
    }

    private static String normalizePeriod(String value) {
        try {
            return YearMonth.parse(value.trim()).toString();
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new BusinessException("月份格式应为 yyyy-MM");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }

    public record SyncedBulletin(long id, String blackboardId, String title, String kind, String plain, String receiver) {
    }

    record Parsed(BigDecimal explicitAmount, int times, String reasonHint) {
    }

    private record Staff(long id, long gradeId) {
    }

    private record StaffName(long id, String name, long gradeId) {
    }
}
