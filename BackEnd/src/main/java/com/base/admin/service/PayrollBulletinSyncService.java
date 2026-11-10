package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.common.PayRewardKinds;
import com.base.admin.domain.dto.PayBulletinQueryDTO;
import com.base.admin.domain.vo.PayBulletinVO;
import com.base.admin.domain.vo.PayDingTalkPullVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.PayrollDingTalkPullService.PeriodWindow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollBulletinSyncService {

    private static final String PERMISSION =
            "钉钉公告权限不足：请开通「读取钉钉公告微应用数据的权限」，并且用公告管理员的钉钉账号在本系统登录一次。";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final DingTalkAuthService dingTalkAuthService;
    private final PayrollDingTalkPullService pullService;
    private final PayrollRewardService rewardService;
    private final PlatformTransactionManager transactionManager;

    public PayDingTalkPullVO sync(String period) {
        PeriodWindow window = pullService.parsePeriod(period);
        List<String> operators = operators();
        if (operators.isEmpty()) {
            throw new BusinessException("请先用公告管理员的钉钉账号在本系统登录一次，才能读取行政通报");
        }
        String token = dingTalkAuthService.requireAppAccessToken();
        String operator = pickOperator(token, operators);
        List<Category> categories = categories(token, operator);
        Map<String, Notice> notices = new LinkedHashMap<>();
        int seen = 0;
        if (categories.isEmpty()) {
            seen += collect(token, operator, window, "", "", notices);
        } else {
            for (Category category : categories) {
                seen += collect(token, operator, window, category.id, category.name, notices);
            }
        }
        List<Notice> kept = new ArrayList<>(notices.values());
        int[] rewardCount = {0};
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            rewardCount[0] = replace(window.period(), kept);
            pullService.mark("bulletin", window.period(), kept.size());
        });
        PayDingTalkPullVO vo = new PayDingTalkPullVO();
        vo.setPeriod(window.period());
        vo.setRecordCount(kept.size());
        vo.setMessage("已查看 " + window.period() + " 公告 " + seen + " 条，写入行政通报 " + kept.size()
                + " 条，生成员工奖惩 " + rewardCount[0] + " 条。核算该月工资前请同时同步考勤。");
        return vo;
    }

    public PageResult<PayBulletinVO> page(PayBulletinQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 200);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(query.getPeriod())) {
            where.append(" AND period = ?");
            args.add(pullService.parsePeriod(query.getPeriod()).period());
        }
        if (StringUtils.hasText(query.getTitle())) {
            where.append(" AND title LIKE ?");
            args.add("%" + query.getTitle().trim() + "%");
        }
        if (StringUtils.hasText(query.getBulletinKind())) {
            where.append(" AND bulletin_kind = ?");
            args.add(query.getBulletinKind().trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(1) FROM pay_bulletin" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<PayBulletinVO> rows = jdbc.query("""
                SELECT id, period, title, category_name, author, bulletin_kind, plain_content,
                       DATE_FORMAT(publish_time, '%Y-%m-%d %H:%i:%s') AS publish_time, receiver_text
                FROM pay_bulletin
                """ + where + " ORDER BY publish_time DESC, id DESC LIMIT ? OFFSET ?", (rs, row) -> {
            PayBulletinVO vo = new PayBulletinVO();
            vo.setId(rs.getLong("id"));
            vo.setPeriod(rs.getString("period"));
            vo.setTitle(rs.getString("title"));
            vo.setCategoryName(rs.getString("category_name"));
            vo.setAuthor(rs.getString("author"));
            vo.setBulletinKind(rs.getString("bulletin_kind"));
            vo.setPlainContent(rs.getString("plain_content"));
            vo.setPublishTime(rs.getString("publish_time"));
            vo.setReceiverText(rs.getString("receiver_text"));
            return vo;
        }, pageArgs.toArray());
        return new PageResult<>(total == null ? 0 : total, rows);
    }

    private List<String> operators() {
        return jdbc.query("""
                SELECT dingtalk_user_id
                FROM hr_user_dingtalk
                WHERE is_active = 1 AND dingtalk_user_id <> ''
                ORDER BY CASE WHEN user_id = 1 THEN 0 ELSE 1 END, user_id
                """, (rs, row) -> rs.getString(1));
    }

    private String pickOperator(String token, List<String> operators) {
        BusinessException last = null;
        for (String operator : operators) {
            try {
                categories(token, operator);
                return operator;
            } catch (BusinessException ex) {
                last = ex;
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if (!message.contains("权限") && !message.contains("管理员") && !message.contains("权限不足")) {
                    throw ex;
                }
            }
        }
        throw last == null ? new BusinessException(PERMISSION) : last;
    }

    private List<Category> categories(String token, String operator) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("operation_userid", operator);
        JsonNode json = dingTalkAuthService.postOapi(token, "topapi/blackboard/category/list", body, PERMISSION);
        JsonNode result = json.path("result");
        List<JsonNode> nodes = new ArrayList<>();
        if (result.isArray()) {
            result.forEach(nodes::add);
        } else if (result.path("category_list").isArray()) {
            result.path("category_list").forEach(nodes::add);
        }
        List<Category> categories = new ArrayList<>();
        for (JsonNode node : nodes) {
            String id = first(node, "id", "category_id");
            if (!StringUtils.hasText(id)) {
                continue;
            }
            categories.add(new Category(id, first(node, "name", "category_name")));
        }
        return categories;
    }

    private int collect(String token, String operator, PeriodWindow window, String categoryId, String categoryName,
                        Map<String, Notice> notices) {
        int seen = 0;
        String start = window.start() + " 00:00:00";
        String end = window.end().plusDays(1) + " 00:00:00";
        for (int page = 1; page <= 30; page++) {
            ObjectNode query = JsonNodeFactory.instance.objectNode();
            query.put("operation_userid", operator);
            query.put("page", page);
            query.put("page_size", 10);
            query.put("start_time", start);
            query.put("end_time", end);
            if (StringUtils.hasText(categoryId)) {
                query.put("category_id", categoryId);
            }
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.set("query_request", query);
            JsonNode json = dingTalkAuthService.postOapi(token, "topapi/blackboard/listids", body, PERMISSION);
            List<String> ids = idsOf(json.path("result"));
            if (ids.isEmpty()) {
                return seen;
            }
            for (String id : ids) {
                seen++;
                Notice notice = detail(token, operator, id, categoryName);
                if (notice != null && keep(notice)) {
                    notices.put(id, notice);
                }
            }
            if (ids.size() < 10) {
                return seen;
            }
        }
        return seen;
    }

    private Notice detail(String token, String operator, String id, String categoryName) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("blackboard_id", id);
        body.put("operation_userid", operator);
        JsonNode result = dingTalkAuthService.postOapi(token, "topapi/blackboard/get", body, PERMISSION).path("result");
        if (result.isMissingNode() || result.isNull()) {
            return null;
        }
        Notice notice = new Notice();
        notice.id = cut(id, 64);
        notice.title = cut(first(result, "title"), 200);
        notice.categoryName = cut(firstNonBlank(first(result, "category_name"), categoryName), 80);
        notice.author = cut(first(result, "author"), 50);
        notice.plain = cut(plain(first(result, "content")), 20000);
        notice.publishTime = parseTime(first(result, "gmt_create"));
        notice.receiver = cut(receivers(result), 500);
        notice.kind = kind(notice.categoryName + notice.title + notice.plain);
        return notice;
    }

    private int replace(String period, List<Notice> notices) {
        jdbc.update("DELETE FROM pay_bulletin WHERE period = ?", period);
        List<PayrollRewardService.SyncedBulletin> synced = new ArrayList<>();
        for (Notice notice : notices) {
            long id = rewardService.insertBulletin(period, notice.id, notice.title, notice.categoryName, notice.author,
                    notice.kind, notice.plain, notice.publishTime, notice.receiver);
            synced.add(new PayrollRewardService.SyncedBulletin(
                    id, notice.id, notice.title, notice.kind, notice.plain, notice.receiver));
        }
        return rewardService.rebuildFromBulletins(period, synced);
    }

    private static boolean keep(Notice notice) {
        String category = notice.categoryName == null ? "" : notice.categoryName;
        if (containsAny(category, "行政", "通报", "奖惩", "奖励", "处分")) {
            return true;
        }
        String text = category + notice.title + notice.plain;
        return containsAny(text, "通报", "表彰", "嘉奖", "奖励", "记功", "处罚", "处分", "警告", "记过", "批评", "惩处");
    }

    private static String kind(String text) {
        String fine = PayRewardKinds.classify(text);
        if (fine != null) {
            return fine;
        }
        return "通报";
    }

    private static boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> idsOf(JsonNode result) {
        List<String> ids = new ArrayList<>();
        JsonNode list = result.isArray() ? result : result.path("list");
        if (!list.isArray()) {
            return ids;
        }
        for (JsonNode node : list) {
            String id = node.isValueNode() ? node.asText("") : first(node, "blackboard_id", "id");
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String receivers(JsonNode result) {
        List<String> parts = new ArrayList<>();
        appendNames(parts, result.path("depname_list"));
        appendNames(parts, result.path("username_list"));
        return String.join("、", parts);
    }

    private static void appendNames(List<String> parts, JsonNode node) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                String name = item.isValueNode() ? item.asText("") : first(item, "name");
                if (StringUtils.hasText(name)) {
                    parts.add(name.trim());
                }
            }
            return;
        }
        String text = node.asText("");
        if (StringUtils.hasText(text) && !"null".equals(text)) {
            parts.add(text.trim());
        }
    }

    private static String plain(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static LocalDateTime parseTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), DATE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (StringUtils.hasText(value) && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
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

    private record Category(String id, String name) {
    }

    private static final class Notice {
        private String id;
        private String title;
        private String categoryName;
        private String author;
        private String kind;
        private String plain;
        private LocalDateTime publishTime;
        private String receiver;
    }
}
