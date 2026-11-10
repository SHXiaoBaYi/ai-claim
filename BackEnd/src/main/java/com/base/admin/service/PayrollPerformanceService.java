package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayPerformanceDTO;
import com.base.admin.domain.dto.PayPerformanceQueryDTO;
import com.base.admin.domain.vo.PayPerformanceVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollPerformanceService {

    private final JdbcTemplate jdbc;

    public PageResult<PayPerformanceVO> page(PayPerformanceQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE p.is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getPeriod())) {
            where.append(" AND p.period = ?");
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
        String from = """
                FROM pay_performance p
                INNER JOIN pay_employee e ON e.id = p.employee_id AND e.is_active = 1
                LEFT JOIN pay_dept d ON d.id = e.dept_id AND d.is_active = 1
                LEFT JOIN pay_post po ON po.id = e.post_id AND po.is_active = 1
                LEFT JOIN pay_grade g ON g.id = e.grade_id AND g.is_active = 1
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(1) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayPerformanceVO> rows = jdbc.query("""
                SELECT p.id, p.employee_id, e.employee_name, d.dept_name, po.post_name, g.grade_name,
                       p.period, p.coefficient, p.remark
                """ + from + where + " ORDER BY p.period DESC, e.employee_name, p.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    PayPerformanceVO vo = new PayPerformanceVO();
                    vo.setId(rs.getLong("id"));
                    vo.setEmployeeId(rs.getLong("employee_id"));
                    vo.setEmployeeName(rs.getString("employee_name"));
                    vo.setDeptName(rs.getString("dept_name"));
                    vo.setPostName(rs.getString("post_name"));
                    vo.setGradeName(rs.getString("grade_name"));
                    vo.setPeriod(rs.getString("period"));
                    vo.setCoefficient(rs.getBigDecimal("coefficient"));
                    vo.setRemark(rs.getString("remark"));
                    return vo;
                }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void save(PayPerformanceDTO dto) {
        String period = normalizePeriod(dto.getPeriod());
        assertActiveEmployee(dto.getEmployeeId());
        BigDecimal coefficient = dto.getCoefficient().setScale(4, RoundingMode.HALF_UP);
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        String user = currentUser();
        if (dto.getId() == null) {
            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(1) FROM pay_performance
                    WHERE is_active = 1 AND employee_id = ? AND period = ?
                    """, Integer.class, dto.getEmployeeId(), period);
            if (exists != null && exists > 0) {
                throw new BusinessException("该员工本月已有绩效考核，请直接编辑");
            }
            List<Long> softIds = jdbc.query("""
                    SELECT id FROM pay_performance
                    WHERE employee_id = ? AND period = ? AND is_active = 0
                    ORDER BY id DESC LIMIT 1
                    """, (rs, row) -> rs.getLong(1), dto.getEmployeeId(), period);
            if (!softIds.isEmpty()) {
                jdbc.update("""
                        UPDATE pay_performance
                        SET coefficient = ?, remark = ?, is_active = 1, update_by = ?
                        WHERE id = ?
                        """, coefficient, remark, user, softIds.get(0));
                return;
            }
            jdbc.update("""
                    INSERT INTO pay_performance (employee_id, period, coefficient, remark, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, dto.getEmployeeId(), period, coefficient, remark, user);
            return;
        }
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_performance
                WHERE is_active = 1 AND employee_id = ? AND period = ? AND id <> ?
                """, Integer.class, dto.getEmployeeId(), period, dto.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("该员工本月已有绩效考核");
        }
        int rows = jdbc.update("""
                UPDATE pay_performance
                SET employee_id = ?, period = ?, coefficient = ?, remark = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, dto.getEmployeeId(), period, coefficient, remark, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("绩效考核记录不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        int rows = jdbc.update("""
                UPDATE pay_performance SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1
                """, currentUser(), id);
        if (rows == 0) {
            throw new BusinessException("绩效考核记录不存在");
        }
    }

    /**
     * 为指定月份补齐在职员工行，默认系数 1；已有记录不覆盖。
     */
    @Transactional
    public int generateMonth(String periodText) {
        String period = normalizePeriod(periodText);
        String user = currentUser();
        List<Long> employeeIds = jdbc.query("""
                SELECT id FROM pay_employee
                WHERE is_active = 1 AND job_status = 1
                ORDER BY id
                """, (rs, row) -> rs.getLong(1));
        int created = 0;
        for (Long employeeId : employeeIds) {
            Integer active = jdbc.queryForObject("""
                    SELECT COUNT(1) FROM pay_performance
                    WHERE employee_id = ? AND period = ? AND is_active = 1
                    """, Integer.class, employeeId, period);
            if (active != null && active > 0) {
                continue;
            }
            List<Long> softIds = jdbc.query("""
                    SELECT id FROM pay_performance
                    WHERE employee_id = ? AND period = ? AND is_active = 0
                    ORDER BY id DESC LIMIT 1
                    """, (rs, row) -> rs.getLong(1), employeeId, period);
            if (!softIds.isEmpty()) {
                jdbc.update("""
                        UPDATE pay_performance
                        SET coefficient = 1.0000, remark = '', is_active = 1, update_by = ?
                        WHERE id = ?
                        """, user, softIds.get(0));
            } else {
                jdbc.update("""
                        INSERT INTO pay_performance (employee_id, period, coefficient, remark, create_by, is_active)
                        VALUES (?, ?, 1.0000, '', ?, 1)
                        """, employeeId, period, user);
            }
            created++;
        }
        return created;
    }

    private void assertActiveEmployee(Long id) {
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
            throw new BusinessException("考核月份格式应为 yyyy-MM");
        }
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }
}
