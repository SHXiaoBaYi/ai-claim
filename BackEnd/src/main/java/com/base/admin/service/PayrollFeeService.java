package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.PayEmployeeFeeDTO;
import com.base.admin.domain.dto.PayEmployeeFeeQueryDTO;
import com.base.admin.domain.dto.PayFeeCategoryDTO;
import com.base.admin.domain.vo.PayEmployeeFeeVO;
import com.base.admin.domain.vo.PayFeeCategoryVO;
import com.base.admin.domain.vo.PayFeeImageVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollFeeService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final RowMapper<PayFeeCategoryVO> CATEGORY_MAPPER = (rs, row) -> {
        PayFeeCategoryVO vo = new PayFeeCategoryVO();
        vo.setId(rs.getLong("id"));
        vo.setCategoryName(rs.getString("category_name"));
        vo.setMonthRule(rs.getString("month_rule"));
        vo.setFeeKind(rs.getString("fee_kind"));
        vo.setSortOrder(rs.getInt("sort_order"));
        return vo;
    };

    private final JdbcTemplate jdbc;

    @Value("${xby.upload-dir:./uploads}")
    private String uploadDir;

    public List<PayFeeCategoryVO> listCategories() {
        return jdbc.query("""
                SELECT id, category_name, month_rule, fee_kind, sort_order
                FROM pay_fee_category
                WHERE is_active = 1
                ORDER BY sort_order, id
                """, CATEGORY_MAPPER);
    }

    @Transactional
    public void saveCategory(PayFeeCategoryDTO dto) {
        String name = dto.getCategoryName().trim();
        String feeKind = normalizeFeeKind(dto.getFeeKind());
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_fee_category
                WHERE is_active = 1 AND category_name = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, name, dto.getId(), dto.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("费用类目已存在");
        }
        String user = currentUser();
        if (dto.getId() == null) {
            Integer maxSort = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(sort_order), 0) FROM pay_fee_category WHERE is_active = 1", Integer.class);
            jdbc.update("""
                    INSERT INTO pay_fee_category (category_name, fee_kind, sort_order, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    """, name, feeKind, (maxSort == null ? 0 : maxSort) + 1, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_fee_category
                SET category_name = ?, fee_kind = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, name, feeKind, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("费用类目不存在");
        }
    }

    private static String normalizeFeeKind(String raw) {
        String kind = raw == null ? "" : raw.trim();
        if ("add".equals(kind) || "deduct".equals(kind)) {
            return kind;
        }
        throw new BusinessException("请选择加钱类或扣费类");
    }

    @Transactional
    public void deleteCategory(Long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee_fee WHERE is_active = 1 AND category_id = ?
                """, Integer.class, id);
        if (used != null && used > 0) {
            throw new BusinessException("该类目下还有费用记录，不能删除");
        }
        jdbc.update("UPDATE pay_fee_category SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1",
                currentUser(), id);
    }

    public PageResult<PayEmployeeFeeVO> page(PayEmployeeFeeQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder("""
                 WHERE f.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getEmployeeName())) {
            where.append(" AND e.employee_name LIKE ?");
            args.add("%" + query.getEmployeeName().trim() + "%");
        }
        if (query.getDeptId() != null) {
            where.append(" AND e.dept_id = ?");
            args.add(query.getDeptId());
        }
        if (query.getCategoryId() != null) {
            where.append(" AND f.category_id = ?");
            args.add(query.getCategoryId());
        }
        if (StringUtils.hasText(query.getFeeMonth())) {
            where.append(" AND f.fee_month = ?");
            args.add(normalizeMonth(query.getFeeMonth()));
        }
        String from = """
                FROM pay_employee_fee f
                JOIN pay_employee e ON e.id = f.employee_id AND e.is_active = 1
                LEFT JOIN pay_dept d ON d.id = e.dept_id
                LEFT JOIN pay_fee_category c ON c.id = f.category_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(1) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayEmployeeFeeVO> rows = jdbc.query("""
                SELECT f.id, f.employee_id, e.employee_name, d.dept_name, f.category_id,
                       c.category_name, c.fee_kind, f.fee_month, f.amount, f.image_url, f.remark
                """ + from + where + " ORDER BY f.fee_month DESC, f.id DESC LIMIT ? OFFSET ?", (rs, row) -> {
            PayEmployeeFeeVO vo = new PayEmployeeFeeVO();
            vo.setId(rs.getLong("id"));
            vo.setEmployeeId(rs.getLong("employee_id"));
            vo.setEmployeeName(rs.getString("employee_name"));
            vo.setDeptName(rs.getString("dept_name"));
            vo.setCategoryId(rs.getLong("category_id"));
            vo.setCategoryName(rs.getString("category_name"));
            vo.setFeeKind(rs.getString("fee_kind"));
            vo.setFeeMonth(rs.getString("fee_month"));
            vo.setAmount(rs.getBigDecimal("amount"));
            vo.setImageUrl(rs.getString("image_url"));
            vo.setRemark(rs.getString("remark"));
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    @Transactional
    public void save(PayEmployeeFeeDTO dto) {
        assertEmployee(dto.getEmployeeId());
        assertCategory(dto.getCategoryId());
        String imageUrl = normalizeImage(dto.getImageUrl());
        String feeMonth = feeMonthFor(dto);
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        BigDecimal amount = dto.getAmount();
        String user = currentUser();
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO pay_employee_fee
                      (employee_id, category_id, fee_month, amount, image_url, remark, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getEmployeeId(), dto.getCategoryId(), feeMonth, amount, imageUrl, remark, user);
            return;
        }
        int rows = jdbc.update("""
                UPDATE pay_employee_fee
                SET employee_id = ?, category_id = ?, fee_month = ?, amount = ?, image_url = ?, remark = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, dto.getEmployeeId(), dto.getCategoryId(), feeMonth, amount, imageUrl, remark, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("费用记录不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        jdbc.update("UPDATE pay_employee_fee SET is_active = 0, update_by = ? WHERE id = ? AND is_active = 1",
                currentUser(), id);
    }

    public PayFeeImageVO saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传截图");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("截图不能超过5MB");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BusinessException("截图只支持 jpg、png、webp、gif");
        }
        String ext = switch (type) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String name = UUID.randomUUID() + ext;
        Path dir = Path.of(uploadDir, "payroll-fee").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name));
        } catch (IOException ex) {
            throw new BusinessException("截图保存失败");
        }
        PayFeeImageVO vo = new PayFeeImageVO();
        vo.setUrl("/uploads/payroll-fee/" + name);
        return vo;
    }

    private void assertEmployee(Long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pay_employee WHERE id = ? AND is_active = 1", Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("员工不存在");
        }
    }

    private void assertCategory(Long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pay_fee_category WHERE id = ? AND is_active = 1", Integer.class, id);
        if (count == null || count == 0) {
            throw new BusinessException("费用类目不存在");
        }
    }

    private String feeMonthFor(PayEmployeeFeeDTO dto) {
        String feeMonth = normalizeMonth(dto.getFeeMonth());
        String rule = jdbc.queryForObject(
                "SELECT month_rule FROM pay_fee_category WHERE id = ? AND is_active = 1",
                String.class, dto.getCategoryId());
        if (!"hire".equals(rule)) {
            return feeMonth;
        }
        Date hireDate = jdbc.queryForObject(
                "SELECT hire_date FROM pay_employee WHERE id = ? AND is_active = 1",
                Date.class, dto.getEmployeeId());
        if (hireDate == null) {
            throw new BusinessException("该员工没有入职时间");
        }
        String hireMonth = hireDate.toLocalDate().toString().substring(0, 7);
        if (!hireMonth.equals(feeMonth)) {
            throw new BusinessException("新入职人员碳14费用只能归属入职当月");
        }
        return hireMonth;
    }

    private static String normalizeMonth(String raw) {
        try {
            return YearMonth.parse(raw.trim()).toString();
        } catch (DateTimeParseException ex) {
            throw new BusinessException("归属月份格式应为 yyyy-MM");
        }
    }

    private static String normalizeImage(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (!url.matches("/uploads/payroll-fee/[A-Za-z0-9._-]+")) {
            throw new BusinessException("请重新上传截图");
        }
        return url;
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }
}
