package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayDeptDTO;
import com.base.admin.domain.dto.PayDeptQueryDTO;
import com.base.admin.domain.dto.PayGradeDTO;
import com.base.admin.domain.dto.PayGradeQueryDTO;
import com.base.admin.domain.dto.PayLateRuleDTO;
import com.base.admin.domain.dto.PayPostDTO;
import com.base.admin.domain.dto.PayPostQueryDTO;
import com.base.admin.domain.vo.PayDeptVO;
import com.base.admin.domain.vo.PayGradeVO;
import com.base.admin.domain.vo.PayLateRuleVO;
import com.base.admin.domain.vo.PayPostVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollBaseService {

    private final JdbcTemplate jdbc;

    private static final RowMapper<PayDeptVO> DEPT_MAPPER = (rs, row) -> {
        PayDeptVO vo = new PayDeptVO();
        vo.setId(rs.getLong("id"));
        vo.setDeptName(rs.getString("dept_name"));
        vo.setSortOrder(rs.getInt("sort_order"));
        vo.setStatus(rs.getInt("status"));
        return vo;
    };

    private static final RowMapper<PayPostVO> POST_MAPPER = (rs, row) -> {
        PayPostVO vo = new PayPostVO();
        vo.setId(rs.getLong("id"));
        vo.setPostName(rs.getString("post_name"));
        vo.setSortOrder(rs.getInt("sort_order"));
        vo.setStatus(rs.getInt("status"));
        return vo;
    };

    private static final RowMapper<PayGradeVO> GRADE_MAPPER = (rs, row) -> {
        PayGradeVO vo = new PayGradeVO();
        vo.setId(rs.getLong("id"));
        vo.setGradeName(rs.getString("grade_name"));
        vo.setSortOrder(rs.getInt("sort_order"));
        vo.setStatus(rs.getInt("status"));
        return vo;
    };

    public List<PayDeptVO> listDepts() {
        return jdbc.query("""
                SELECT id, dept_name, sort_order, status
                FROM pay_dept WHERE is_active = 1
                ORDER BY sort_order, id
                """, DEPT_MAPPER);
    }

    public PageResult<PayDeptVO> pageDepts(PayDeptQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getDeptName())) {
            where.append(" AND dept_name LIKE ?");
            args.add("%" + query.getDeptName().trim() + "%");
        }
        if (query.getStatus() != null) {
            where.append(" AND status = ?");
            args.add(query.getStatus());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_dept" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayDeptVO> rows = jdbc.query("""
                SELECT id, dept_name, sort_order, status
                FROM pay_dept
                """ + where + " ORDER BY sort_order, id LIMIT ? OFFSET ?", DEPT_MAPPER, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void saveDept(PayDeptDTO dto) {
        String name = dto.getDeptName().trim();
        int sort = dto.getSortOrder() == null ? 0 : dto.getSortOrder();
        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        String user = currentUser();
        assertDeptNameUnique(name, dto.getId());
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_dept (dept_name, sort_order, status, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    """, name, sort, status, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_dept
                SET dept_name = ?, sort_order = ?, status = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, name, sort, status, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("部门不存在");
        }
    }

    @Transactional
    public void deleteDept(Long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee WHERE is_active = 1 AND dept_id = ?
                """, Integer.class, id);
        if (used != null && used > 0) {
            throw new BusinessException("该部门下还有员工，不能删除");
        }
        jdbc.update("UPDATE pay_dept SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1", currentUser(), id);
    }

    public List<PayPostVO> listPosts() {
        return jdbc.query("""
                SELECT id, post_name, sort_order, status
                FROM pay_post WHERE is_active = 1
                ORDER BY sort_order, id
                """, POST_MAPPER);
    }

    public PageResult<PayPostVO> pagePosts(PayPostQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getPostName())) {
            where.append(" AND post_name LIKE ?");
            args.add("%" + query.getPostName().trim() + "%");
        }
        if (query.getStatus() != null) {
            where.append(" AND status = ?");
            args.add(query.getStatus());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_post" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayPostVO> rows = jdbc.query("""
                SELECT id, post_name, sort_order, status
                FROM pay_post
                """ + where + " ORDER BY sort_order, id LIMIT ? OFFSET ?", POST_MAPPER, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void savePost(PayPostDTO dto) {
        String name = dto.getPostName().trim();
        int sort = dto.getSortOrder() == null ? 0 : dto.getSortOrder();
        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        String user = currentUser();
        assertPostNameUnique(name, dto.getId());
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_post (post_name, sort_order, status, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    """, name, sort, status, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_post
                SET post_name = ?, sort_order = ?, status = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, name, sort, status, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("岗位不存在");
        }
    }

    @Transactional
    public void deletePost(Long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee WHERE is_active = 1 AND post_id = ?
                """, Integer.class, id);
        if (used != null && used > 0) {
            throw new BusinessException("该岗位下还有员工，不能删除");
        }
        jdbc.update("UPDATE pay_post SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1", currentUser(), id);
    }

    public List<PayGradeVO> listGrades() {
        return jdbc.query("""
                SELECT id, grade_name, sort_order, status
                FROM pay_grade WHERE is_active = 1
                ORDER BY sort_order, id
                """, GRADE_MAPPER);
    }

    public PageResult<PayGradeVO> pageGrades(PayGradeQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getGradeName())) {
            where.append(" AND grade_name LIKE ?");
            args.add("%" + query.getGradeName().trim() + "%");
        }
        if (query.getStatus() != null) {
            where.append(" AND status = ?");
            args.add(query.getStatus());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_grade" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayGradeVO> rows = jdbc.query("""
                SELECT id, grade_name, sort_order, status
                FROM pay_grade
                """ + where + " ORDER BY sort_order, id LIMIT ? OFFSET ?", GRADE_MAPPER, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void saveGrade(PayGradeDTO dto) {
        String name = dto.getGradeName().trim();
        int sort = dto.getSortOrder() == null ? 0 : dto.getSortOrder();
        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        String user = currentUser();
        assertGradeNameUnique(name, dto.getId());
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_grade (grade_name, sort_order, status, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    """, name, sort, status, user);
            Long gradeId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("""
                    INSERT INTO pay_late_rule
                      (grade_id, allow_count, personal1_amount, personal2_amount, reward_amount, punish_amount, create_by, is_active)
                    VALUES (?, 3, 0, 0, 0, 0, ?, 1)
                    """, gradeId, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_grade
                SET grade_name = ?, sort_order = ?, status = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, name, sort, status, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("职级不存在");
        }
        ensureLateRule(dto.getId(), user);
    }

    @Transactional
    public void deleteGrade(Long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee WHERE is_active = 1 AND grade_id = ?
                """, Integer.class, id);
        if (used != null && used > 0) {
            throw new BusinessException("该职级下还有员工，不能删除");
        }
        jdbc.update("UPDATE pay_late_rule SET is_active = 0, update_by = ? WHERE grade_id = ? AND is_active = 1",
                currentUser(), id);
        jdbc.update("UPDATE pay_grade SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1",
                currentUser(), id);
    }

    public List<PayLateRuleVO> listLateRules() {
        return jdbc.query("""
                SELECT r.id, r.grade_id, g.grade_name, r.allow_count, r.personal1_amount, r.personal2_amount,
                       r.amt_verbal_praise, r.amt_commendation, r.amt_merit, r.amt_major_merit,
                       r.amt_verbal_critic, r.amt_bulletin_critic, r.amt_demerit, r.amt_major_demerit
                FROM pay_late_rule r
                INNER JOIN pay_grade g ON g.id = r.grade_id AND g.is_active = 1
                WHERE r.is_active = 1
                ORDER BY g.sort_order, g.id
                """, (rs, row) -> {
            PayLateRuleVO vo = new PayLateRuleVO();
            vo.setId(rs.getLong("id"));
            vo.setGradeId(rs.getLong("grade_id"));
            vo.setGradeName(rs.getString("grade_name"));
            vo.setAllowCount(rs.getInt("allow_count"));
            vo.setPersonal1Amount(rs.getBigDecimal("personal1_amount"));
            vo.setPersonal2Amount(rs.getBigDecimal("personal2_amount"));
            vo.setAmtVerbalPraise(rs.getBigDecimal("amt_verbal_praise"));
            vo.setAmtCommendation(rs.getBigDecimal("amt_commendation"));
            vo.setAmtMerit(rs.getBigDecimal("amt_merit"));
            vo.setAmtMajorMerit(rs.getBigDecimal("amt_major_merit"));
            vo.setAmtVerbalCritic(rs.getBigDecimal("amt_verbal_critic"));
            vo.setAmtBulletinCritic(rs.getBigDecimal("amt_bulletin_critic"));
            vo.setAmtDemerit(rs.getBigDecimal("amt_demerit"));
            vo.setAmtMajorDemerit(rs.getBigDecimal("amt_major_demerit"));
            return vo;
        });
    }

    @Transactional
    public void saveLateRule(PayLateRuleDTO dto) {
        int rows = jdbc.update("""
                UPDATE pay_late_rule
                SET allow_count = ?, personal1_amount = ?, personal2_amount = ?,
                    amt_verbal_praise = ?, amt_commendation = ?, amt_merit = ?, amt_major_merit = ?,
                    amt_verbal_critic = ?, amt_bulletin_critic = ?, amt_demerit = ?, amt_major_demerit = ?,
                    reward_amount = ?, punish_amount = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, dto.getAllowCount(), dto.getPersonal1Amount(), dto.getPersonal2Amount(),
                dto.getAmtVerbalPraise(), dto.getAmtCommendation(), dto.getAmtMerit(), dto.getAmtMajorMerit(),
                dto.getAmtVerbalCritic(), dto.getAmtBulletinCritic(), dto.getAmtDemerit(), dto.getAmtMajorDemerit(),
                dto.getAmtCommendation(), dto.getAmtBulletinCritic(), currentUser(), dto.getId());
        if (rows == 0) {
            throw new BusinessException("职级规则不存在");
        }
    }

    /**
     * 按当月迟到+早退次数 C 计算扣款：C≥1 扣事假1整月定额（超门槛仍扣）；
     * C 超过 allowCount 时再扣 (C - allowCount) × 事假2。
     */
    public static BigDecimal[] lateDeduct(int lateCount, int allowCount, BigDecimal personal1, BigDecimal personal2) {
        BigDecimal p1 = BigDecimal.ZERO;
        BigDecimal p2 = BigDecimal.ZERO;
        if (lateCount > 0) {
            p1 = personal1 == null ? BigDecimal.ZERO : personal1;
        }
        if (lateCount > allowCount) {
            int times = lateCount - Math.max(allowCount, 0);
            p2 = (personal2 == null ? BigDecimal.ZERO : personal2).multiply(BigDecimal.valueOf(times));
        }
        return new BigDecimal[]{p1, p2};
    }

    private void ensureLateRule(Long gradeId, String user) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_late_rule WHERE grade_id = ? AND is_active = 1
                """, Integer.class, gradeId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO pay_late_rule
                  (grade_id, allow_count, personal1_amount, personal2_amount, reward_amount, punish_amount, create_by, is_active)
                VALUES (?, 3, 0, 0, 0, 0, ?, 1)
                """, gradeId, user);
    }

    private void assertDeptNameUnique(String name, Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_dept
                WHERE is_active = 1 AND dept_name = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, name, id, id);
        if (count != null && count > 0) {
            throw new BusinessException("部门名称已存在");
        }
    }

    private void assertPostNameUnique(String name, Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_post
                WHERE is_active = 1 AND post_name = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, name, id, id);
        if (count != null && count > 0) {
            throw new BusinessException("岗位名称已存在");
        }
    }

    private void assertGradeNameUnique(String name, Long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_grade
                WHERE is_active = 1 AND grade_name = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, name, id, id);
        if (count != null && count > 0) {
            throw new BusinessException("职级名称已存在");
        }
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }
}
