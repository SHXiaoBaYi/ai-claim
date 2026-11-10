package com.base.admin.service;

import com.base.admin.domain.vo.PayDingTalkSyncVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把钉钉企业通讯录同步到算薪部门和员工。工资、身份证、银行卡以系统里已填写的为准。
 */
@Service
@RequiredArgsConstructor
public class PayrollDingTalkSyncService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String EMPTY_POST = "未设置岗位";

    private final JdbcTemplate jdbc;
    private final DingTalkAuthService dingTalkAuthService;
    private final PlatformTransactionManager transactionManager;

    public PayDingTalkSyncVO sync() {
        String token = dingTalkAuthService.requireAppAccessToken();
        Map<Long, DeptNode> depts = new LinkedHashMap<>();
        Map<String, DingUser> users = new LinkedHashMap<>();
        walkDept(token, 1, 0, new HashSet<>(), depts, users);
        if (users.isEmpty()) {
            throw new BusinessException("钉钉通讯录没有返回员工。请确认应用已开通「通讯录部门信息读权限」和「成员信息读权限」");
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PayDingTalkSyncVO result = tx.execute(status -> save(depts, users));
        if (result == null) {
            throw new BusinessException("同步失败");
        }
        return result;
    }

    private void walkDept(String token, long deptId, int depth, Set<Long> visited,
                          Map<Long, DeptNode> depts, Map<String, DingUser> users) {
        if (depth > 20 || !visited.add(deptId)) {
            return;
        }
        JsonNode detail = dingTalkAuthService.postOapi(token, "topapi/v2/department/get", body("dept_id", deptId));
        JsonNode dept = detail.path("result");
        String name = text(dept, "name");
        if (!StringUtils.hasText(name)) {
            name = deptId == 1 ? "公司" : "部门" + deptId;
        }
        depts.put(deptId, new DeptNode(deptId, cut(name, 50)));
        collectUsers(token, deptId, depth, users);
        JsonNode children = dingTalkAuthService.postOapi(token, "topapi/v2/department/listsub", body("dept_id", deptId));
        JsonNode list = children.path("result");
        if (!list.isArray()) {
            return;
        }
        for (JsonNode child : list) {
            long childId = child.path("dept_id").asLong(0);
            if (childId > 0) {
                walkDept(token, childId, depth + 1, visited, depts, users);
            }
        }
    }

    private void collectUsers(String token, long deptId, int depth, Map<String, DingUser> users) {
        long cursor = 0;
        for (int page = 0; page < 200; page++) {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.put("dept_id", deptId);
            body.put("cursor", cursor);
            body.put("size", 100);
            body.put("language", "zh_CN");
            JsonNode json = dingTalkAuthService.postOapi(token, "topapi/v2/user/list", body);
            JsonNode result = json.path("result");
            JsonNode list = result.path("list");
            if (list.isArray()) {
                for (JsonNode node : list) {
                    mergeUser(users, node, deptId, depth);
                }
            }
            if (!result.path("has_more").asBoolean(false)) {
                return;
            }
            long next = result.path("next_cursor").asLong(cursor);
            if (next == cursor) {
                return;
            }
            cursor = next;
        }
    }

    private void mergeUser(Map<String, DingUser> users, JsonNode node, long deptId, int depth) {
        String userid = text(node, "userid");
        String name = text(node, "name");
        if (!StringUtils.hasText(userid) || !StringUtils.hasText(name)) {
            return;
        }
        DingUser user = users.computeIfAbsent(userid, key -> new DingUser(key));
        user.name = cut(name, 50);
        String mobile = DingTalkAuthService.normalizeMobile(text(node, "mobile"));
        if (StringUtils.hasText(mobile)) {
            user.phone = cut(mobile, 20);
        }
        String jobNumber = text(node, "job_number");
        if (StringUtils.hasText(jobNumber)) {
            user.jobNumber = cut(jobNumber, 32);
        }
        String title = text(node, "title");
        if (StringUtils.hasText(title)) {
            user.title = cut(title, 50);
        }
        long hired = node.path("hired_date").asLong(0);
        if (hired > 0) {
            user.hiredDate = hired;
        }
        if (node.has("active")) {
            user.active = node.path("active").asBoolean(true);
        }
        if (depth >= user.deptDepth) {
            user.deptId = deptId;
            user.deptDepth = depth;
        }
    }

    private PayDingTalkSyncVO save(Map<Long, DeptNode> depts, Map<String, DingUser> users) {
        String operator = currentUser();
        Map<Long, Long> deptIds = new HashMap<>();
        for (DeptNode dept : depts.values()) {
            deptIds.put(dept.dingId, upsertDept(dept, operator));
        }
        Map<String, Long> postIds = new HashMap<>();
        PayDingTalkSyncVO vo = new PayDingTalkSyncVO();
        vo.setDeptCount(depts.size());
        vo.setEmployeeCount(users.size());
        Set<String> seen = users.keySet();
        vo.setResigned(markMissingResigned(seen, operator));
        for (DingUser user : users.values()) {
            Long deptId = deptIds.get(user.deptId);
            if (deptId == null) {
                deptId = deptIds.get(1L);
            }
            if (deptId == null) {
                throw new BusinessException("钉钉部门未能写入，无法保存员工");
            }
            String postName = StringUtils.hasText(user.title) ? user.title : EMPTY_POST;
            Long postId = postIds.computeIfAbsent(postName, name -> upsertPost(name, operator));
            if (upsertEmployee(user, deptId, postId, operator)) {
                vo.setCreated(vo.getCreated() + 1);
            } else {
                vo.setUpdated(vo.getUpdated() + 1);
            }
        }
        return vo;
    }

    private long upsertDept(DeptNode dept, String operator) {
        List<Long> byDing = jdbc.query("""
                SELECT id FROM pay_dept WHERE is_active = 1 AND dingtalk_dept_id = ? ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong(1), dept.dingId);
        if (!byDing.isEmpty()) {
            jdbc.update("""
                    UPDATE pay_dept SET dept_name = ?, status = 0, update_by = ? WHERE id = ?
                    """, uniqueDeptName(dept.name, dept.dingId, byDing.get(0)), operator, byDing.get(0));
            return byDing.get(0);
        }
        List<Long> byName = jdbc.query("""
                SELECT id FROM pay_dept
                WHERE is_active = 1 AND dept_name = ? AND dingtalk_dept_id = 0
                ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong(1), dept.name);
        if (!byName.isEmpty()) {
            jdbc.update("""
                    UPDATE pay_dept SET dingtalk_dept_id = ?, status = 0, update_by = ? WHERE id = ?
                    """, dept.dingId, operator, byName.get(0));
            return byName.get(0);
        }
        jdbc.update("""
                INSERT INTO pay_dept (dept_name, sort_order, status, dingtalk_dept_id, create_by, is_active)
                VALUES (?, 0, 0, ?, ?, 1)
                """, uniqueDeptName(dept.name, dept.dingId, null), dept.dingId, operator);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new BusinessException("保存部门失败");
        }
        return id;
    }

    private String uniqueDeptName(String name, long dingId, Long selfId) {
        if (!deptNameTaken(name, selfId)) {
            return name;
        }
        String suffixed = cut(name, 40) + "(" + dingId + ")";
        return cut(suffixed, 50);
    }

    private boolean deptNameTaken(String name, Long selfId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_dept
                WHERE is_active = 1 AND dept_name = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, name, selfId, selfId);
        return count != null && count > 0;
    }

    private long upsertPost(String postName, String operator) {
        List<Long> found = jdbc.query("""
                SELECT id FROM pay_post WHERE is_active = 1 AND post_name = ? ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong(1), postName);
        if (!found.isEmpty()) {
            jdbc.update("UPDATE pay_post SET status = 0, update_by = ? WHERE id = ?", operator, found.get(0));
            return found.get(0);
        }
        jdbc.update("""
                INSERT INTO pay_post (post_name, sort_order, status, create_by, is_active)
                VALUES (?, 0, 0, ?, 1)
                """, postName, operator);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new BusinessException("保存岗位失败");
        }
        return id;
    }

    /** @return true 表示新增 */
    private boolean upsertEmployee(DingUser user, long deptId, long postId, String operator) {
        Existing existing = findExisting(user);
        Date hireDate = hireDate(user.hiredDate);
        int jobStatus = user.active ? 1 : 0;
        if (existing == null) {
            String employeeNo = pickEmployeeNo(user.jobNumber, user.userid, null);
            jdbc.update("""
                    INSERT INTO pay_employee
                      (dept_id, post_id, employee_name, hire_date, salary_standard, employee_no, phone,
                       id_card, bank_account, bank_name, job_status, dingtalk_user_id, create_by, is_active)
                    VALUES (?, ?, ?, ?, 0, ?, ?, '', '', '', ?, ?, ?, 1)
                    """, deptId, postId, user.name, hireDate == null ? Date.valueOf(LocalDate.now(SHANGHAI)) : hireDate,
                    employeeNo, user.phone == null ? "" : user.phone, jobStatus, cut(user.userid, 64), operator);
            return true;
        }
        String employeeNo = StringUtils.hasText(user.jobNumber)
                ? pickEmployeeNo(user.jobNumber, user.userid, existing.id)
                : (StringUtils.hasText(existing.employeeNo) ? existing.employeeNo : pickEmployeeNo("", user.userid, existing.id));
        String phone = StringUtils.hasText(user.phone) ? user.phone : existing.phone;
        Date nextHire = hireDate == null ? existing.hireDate : hireDate;
        jdbc.update("""
                UPDATE pay_employee
                SET dept_id = ?, post_id = ?, employee_name = ?, hire_date = ?, employee_no = ?, phone = ?,
                    job_status = ?, dingtalk_user_id = ?, is_active = 1, update_by = ?
                WHERE id = ?
                """, deptId, postId, user.name, nextHire, employeeNo, phone == null ? "" : phone,
                jobStatus, cut(user.userid, 64), operator, existing.id);
        return false;
    }

    private Existing findExisting(DingUser user) {
        List<Existing> byDing = jdbc.query("""
                SELECT id, employee_no, phone, hire_date, dingtalk_user_id FROM pay_employee
                WHERE dingtalk_user_id = ? ORDER BY is_active DESC, id LIMIT 1
                """, EXISTING_MAPPER, user.userid);
        if (!byDing.isEmpty()) {
            return byDing.get(0);
        }
        if (StringUtils.hasText(user.phone)) {
            List<Existing> byPhone = jdbc.query("""
                    SELECT id, employee_no, phone, hire_date, dingtalk_user_id FROM pay_employee
                    WHERE is_active = 1 AND phone = ? ORDER BY id LIMIT 1
                    """, EXISTING_MAPPER, user.phone);
            if (!byPhone.isEmpty() && canBind(byPhone.get(0), user)) {
                return byPhone.get(0);
            }
        }
        if (StringUtils.hasText(user.jobNumber)) {
            List<Existing> byNo = jdbc.query("""
                    SELECT id, employee_no, phone, hire_date, dingtalk_user_id FROM pay_employee
                    WHERE is_active = 1 AND employee_no = ? ORDER BY id LIMIT 1
                    """, EXISTING_MAPPER, user.jobNumber);
            if (!byNo.isEmpty() && canBind(byNo.get(0), user)) {
                return byNo.get(0);
            }
        }
        return null;
    }

    /** 手机号或工号已经绑了另一个钉钉账号时，另建一条，避免后一个人盖掉前一个人。 */
    private static boolean canBind(Existing existing, DingUser user) {
        return !StringUtils.hasText(existing.dingUserId) || existing.dingUserId.equals(user.userid);
    }

    private int markMissingResigned(Set<String> seen, String operator) {
        List<String> linked = jdbc.query("""
                SELECT dingtalk_user_id FROM pay_employee
                WHERE is_active = 1 AND job_status = 1 AND dingtalk_user_id <> ''
                """, (rs, row) -> rs.getString(1));
        int resigned = 0;
        for (String dingId : linked) {
            if (seen.contains(dingId)) {
                continue;
            }
            resigned += jdbc.update("""
                    UPDATE pay_employee SET job_status = 0, update_by = ?
                    WHERE is_active = 1 AND job_status = 1 AND dingtalk_user_id = ?
                    """, operator, dingId);
        }
        return resigned;
    }

    private String pickEmployeeNo(String jobNumber, String userid, Long selfId) {
        String preferred = StringUtils.hasText(jobNumber) ? jobNumber : cut(userid, 32);
        if (!employeeNoTaken(preferred, selfId)) {
            return preferred;
        }
        String fallback = cut(userid, 32);
        if (!employeeNoTaken(fallback, selfId)) {
            return fallback;
        }
        return "";
    }

    private boolean employeeNoTaken(String employeeNo, Long selfId) {
        if (!StringUtils.hasText(employeeNo)) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM pay_employee
                WHERE is_active = 1 AND employee_no = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, employeeNo, selfId, selfId);
        return count != null && count > 0;
    }

    private static Date hireDate(long hired) {
        if (hired <= 0) {
            return null;
        }
        long millis = hired < 10_000_000_000L ? hired * 1000 : hired;
        return Date.valueOf(Instant.ofEpochMilli(millis).atZone(SHANGHAI).toLocalDate());
    }

    private ObjectNode body(String field, long value) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put(field, value);
        body.put("language", "zh_CN");
        return body;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }

    private static final org.springframework.jdbc.core.RowMapper<Existing> EXISTING_MAPPER = (rs, row) -> {
        Existing existing = new Existing();
        existing.id = rs.getLong("id");
        existing.employeeNo = rs.getString("employee_no");
        existing.phone = rs.getString("phone");
        existing.dingUserId = rs.getString("dingtalk_user_id");
        Date hire = rs.getDate("hire_date");
        existing.hireDate = hire == null ? Date.valueOf(LocalDate.now(SHANGHAI)) : hire;
        return existing;
    };

    private static final class DeptNode {
        private final long dingId;
        private final String name;

        private DeptNode(long dingId, String name) {
            this.dingId = dingId;
            this.name = name;
        }
    }

    private static final class DingUser {
        private final String userid;
        private String name = "";
        private String phone = "";
        private String jobNumber = "";
        private String title = "";
        private long hiredDate;
        private boolean active = true;
        private long deptId = 1;
        private int deptDepth = -1;

        private DingUser(String userid) {
            this.userid = userid;
        }
    }

    private static final class Existing {
        private long id;
        private String employeeNo;
        private String phone;
        private String dingUserId;
        private Date hireDate;
    }
}
