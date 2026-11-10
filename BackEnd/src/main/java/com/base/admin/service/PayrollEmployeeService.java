package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayEmployeeDTO;
import com.base.admin.domain.dto.PayEmployeeQueryDTO;
import com.base.admin.domain.vo.PayEmployeeVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollEmployeeService {

    private final JdbcTemplate jdbc;

    public PageResult<PayEmployeeVO> list(PayEmployeeQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE e.is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getEmployeeName())) {
            where.append(" AND e.employee_name LIKE ?");
            args.add("%" + query.getEmployeeName().trim() + "%");
        }
        if (query.getDeptId() != null) {
            where.append(" AND e.dept_id = ?");
            args.add(query.getDeptId());
        }
        if (query.getPostId() != null) {
            where.append(" AND e.post_id = ?");
            args.add(query.getPostId());
        }
        if (query.getGradeId() != null) {
            where.append(" AND e.grade_id = ?");
            args.add(query.getGradeId());
        }
        if (query.getJobStatus() != null) {
            where.append(" AND e.job_status = ?");
            args.add(query.getJobStatus());
        }
        String from = """
                FROM pay_employee e
                LEFT JOIN pay_dept d ON d.id = e.dept_id AND d.is_active = 1
                LEFT JOIN pay_post p ON p.id = e.post_id AND p.is_active = 1
                LEFT JOIN pay_grade g ON g.id = e.grade_id AND g.is_active = 1
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(1) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayEmployeeVO> rows = jdbc.query("""
                SELECT e.id, e.dept_id, d.dept_name, e.post_id, p.post_name, e.grade_id, g.grade_name,
                       e.employee_name, e.hire_date, e.salary_standard,
                       e.amt_post, e.amt_full_attendance, e.amt_social_subsidy, e.amt_housing_subsidy,
                       e.amt_transport, e.amt_noncompete, e.amt_hygiene, e.amt_fixed_overtime,
                       e.employee_no, e.phone, e.id_card, e.bank_account, e.bank_name, e.job_status,
                       e.hukou_type, e.social_base, e.social_account
                """ + from + where + " ORDER BY e.id DESC LIMIT ? OFFSET ?", EMPLOYEE_MAPPER, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void save(PayEmployeeDTO dto) {
        assertActiveDept(dto.getDeptId());
        assertActivePost(dto.getPostId());
        assertActiveGrade(dto.getGradeId());
        String employeeNo = blank(dto.getEmployeeNo());
        String idCard = blank(dto.getIdCard());
        assertUnique("employee_no", "工号", employeeNo, dto.getId());
        assertUnique("id_card", "身份证号", idCard, dto.getId());
        int jobStatus = dto.getJobStatus() == null ? 1 : dto.getJobStatus();
        BigDecimal amtPost = money(dto.getAmtPost());
        BigDecimal amtFull = money(dto.getAmtFullAttendance());
        BigDecimal amtSocial = money(dto.getAmtSocialSubsidy());
        BigDecimal amtHousing = money(dto.getAmtHousingSubsidy());
        BigDecimal amtTransport = money(dto.getAmtTransport());
        BigDecimal amtNoncompete = money(dto.getAmtNoncompete());
        BigDecimal amtHygiene = money(dto.getAmtHygiene());
        BigDecimal amtFixedOt = money(dto.getAmtFixedOvertime());
        BigDecimal salary = amtPost.add(amtFull).add(amtSocial).add(amtHousing)
                .add(amtTransport).add(amtNoncompete).add(amtHygiene).add(amtFixedOt)
                .setScale(2, RoundingMode.HALF_UP);
        String user = currentUser();
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_employee
                      (dept_id, post_id, grade_id, employee_name, hire_date, salary_standard,
                       amt_post, amt_full_attendance, amt_social_subsidy, amt_housing_subsidy,
                       amt_transport, amt_noncompete, amt_hygiene, amt_fixed_overtime,
                       employee_no, phone, id_card, bank_account, bank_name, job_status,
                       hukou_type, social_base, social_account, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getDeptId(), dto.getPostId(), dto.getGradeId(), dto.getEmployeeName().trim(),
                    Date.valueOf(dto.getHireDate()), salary,
                    amtPost, amtFull, amtSocial, amtHousing, amtTransport, amtNoncompete, amtHygiene, amtFixedOt,
                    employeeNo, blank(dto.getPhone()), idCard, blank(dto.getBankAccount()), blank(dto.getBankName()),
                    jobStatus, hukou(dto.getHukouType()), dto.getSocialBase(), blank(dto.getSocialAccount()), user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_employee
                SET dept_id = ?, post_id = ?, grade_id = ?, employee_name = ?, hire_date = ?, salary_standard = ?,
                    amt_post = ?, amt_full_attendance = ?, amt_social_subsidy = ?, amt_housing_subsidy = ?,
                    amt_transport = ?, amt_noncompete = ?, amt_hygiene = ?, amt_fixed_overtime = ?,
                    employee_no = ?, phone = ?, id_card = ?, bank_account = ?, bank_name = ?, job_status = ?,
                    hukou_type = ?, social_base = ?, social_account = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, dto.getDeptId(), dto.getPostId(), dto.getGradeId(), dto.getEmployeeName().trim(),
                Date.valueOf(dto.getHireDate()), salary,
                amtPost, amtFull, amtSocial, amtHousing, amtTransport, amtNoncompete, amtHygiene, amtFixedOt,
                employeeNo, blank(dto.getPhone()), idCard, blank(dto.getBankAccount()), blank(dto.getBankName()),
                jobStatus, hukou(dto.getHukouType()), dto.getSocialBase(), blank(dto.getSocialAccount()), user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("员工不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        int rows = jdbc.update("""
                UPDATE pay_employee SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1
                """, currentUser(), id);
        if (rows == 0) {
            throw new BusinessException("员工不存在");
        }
    }

    private void assertActiveDept(Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_dept WHERE id = ? AND is_active = 1 AND status = 0
                """, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("部门不存在或已停用，请先在部门页维护");
        }
    }

    private void assertActivePost(Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_post WHERE id = ? AND is_active = 1 AND status = 0
                """, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("岗位不存在或已停用，请先在岗位页维护");
        }
    }

    private void assertActiveGrade(Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_grade WHERE id = ? AND is_active = 1 AND status = 0
                """, Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("职级不存在或已停用，请先在职级页维护");
        }
    }

    private void assertUnique(String column, String label, String value, Long id) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee
                WHERE is_active = 1 AND %s = ? AND (? IS NULL OR id <> ?)
                """.formatted(column), Integer.class, value, id, id);
        if (count != null && count > 0) {
            throw new BusinessException(label + "已存在");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String hukou(String value) {
        String text = blank(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (!"城镇".equals(text) && !"农村".equals(text)) {
            throw new BusinessException("户口性质只能是城镇或农村");
        }
        return text;
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }

    private static final RowMapper<PayEmployeeVO> EMPLOYEE_MAPPER = new RowMapper<>() {
        @Override
        public PayEmployeeVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            PayEmployeeVO vo = new PayEmployeeVO();
            vo.setId(rs.getLong("id"));
            vo.setDeptId(rs.getLong("dept_id"));
            vo.setDeptName(rs.getString("dept_name"));
            vo.setPostId(rs.getLong("post_id"));
            vo.setPostName(rs.getString("post_name"));
            long gradeId = rs.getLong("grade_id");
            vo.setGradeId(rs.wasNull() || gradeId == 0 ? null : gradeId);
            vo.setGradeName(rs.getString("grade_name"));
            vo.setEmployeeName(rs.getString("employee_name"));
            Date hireDate = rs.getDate("hire_date");
            vo.setHireDate(hireDate == null ? null : hireDate.toLocalDate());
            vo.setSalaryStandard(rs.getBigDecimal("salary_standard"));
            vo.setAmtPost(rs.getBigDecimal("amt_post"));
            vo.setAmtFullAttendance(rs.getBigDecimal("amt_full_attendance"));
            vo.setAmtSocialSubsidy(rs.getBigDecimal("amt_social_subsidy"));
            vo.setAmtHousingSubsidy(rs.getBigDecimal("amt_housing_subsidy"));
            vo.setAmtTransport(rs.getBigDecimal("amt_transport"));
            vo.setAmtNoncompete(rs.getBigDecimal("amt_noncompete"));
            vo.setAmtHygiene(rs.getBigDecimal("amt_hygiene"));
            vo.setAmtFixedOvertime(rs.getBigDecimal("amt_fixed_overtime"));
            vo.setEmployeeNo(rs.getString("employee_no"));
            vo.setPhone(rs.getString("phone"));
            vo.setIdCard(rs.getString("id_card"));
            vo.setBankAccount(rs.getString("bank_account"));
            vo.setBankName(rs.getString("bank_name"));
            vo.setJobStatus(rs.getInt("job_status"));
            vo.setHukouType(rs.getString("hukou_type"));
            vo.setSocialBase(rs.getBigDecimal("social_base"));
            vo.setSocialAccount(rs.getString("social_account"));
            return vo;
        }
    };
}
