package com.base.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按员工缴费基数 + 户口性质匹配社保规则，计算个人社保、公积金扣款。
 */
@Service
@RequiredArgsConstructor
public class PayrollSocialCalcService {

    private static final String PREFERRED_REGION = "上海";
    private static final String HOUSING = "housing";

    private final JdbcTemplate jdbc;

    public record PersonalDeduct(BigDecimal social, BigDecimal housing, String ruleLabel) {
        public static PersonalDeduct zero() {
            return new PersonalDeduct(BigDecimal.ZERO, BigDecimal.ZERO, "");
        }
    }

    public Map<Long, PersonalDeduct> calcForEmployees(List<EmployeeSocial> employees) {
        List<Rule> rules = loadRules();
        Map<Long, PersonalDeduct> result = new HashMap<>();
        for (EmployeeSocial emp : employees) {
            result.put(emp.id(), calcOne(emp, rules));
        }
        return result;
    }

    public PersonalDeduct calcOne(EmployeeSocial emp, List<Rule> rules) {
        if (emp.socialBase() == null || emp.socialBase().compareTo(BigDecimal.ZERO) <= 0) {
            return PersonalDeduct.zero();
        }
        Rule rule = matchRule(emp.hukouType(), emp.socialBase(), rules);
        if (rule == null) {
            return PersonalDeduct.zero();
        }
        BigDecimal social = BigDecimal.ZERO;
        BigDecimal housing = BigDecimal.ZERO;
        for (Item item : rule.items) {
            BigDecimal base = clamp(emp.socialBase(),
                    item.baseMin != null ? item.baseMin : rule.baseMin,
                    item.baseMax != null ? item.baseMax : rule.baseMax);
            BigDecimal personal = base.multiply(item.personalRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (HOUSING.equals(item.itemCode)) {
                housing = housing.add(personal);
            } else {
                social = social.add(personal);
            }
        }
        String label = rule.regionName + rule.hukouType;
        return new PersonalDeduct(social, housing, label);
    }

    public List<Rule> loadRules() {
        Map<Long, Rule> map = new HashMap<>();
        jdbc.query("""
                SELECT id, rule_code, region_name, hukou_type, base_min, base_max, sort_order
                FROM pay_social_rule WHERE is_active = 1
                ORDER BY sort_order, id
                """, rs -> {
            Rule rule = new Rule();
            rule.id = rs.getLong("id");
            rule.ruleCode = rs.getString("rule_code");
            rule.regionName = rs.getString("region_name");
            rule.hukouType = rs.getString("hukou_type");
            rule.baseMin = rs.getBigDecimal("base_min");
            rule.baseMax = rs.getBigDecimal("base_max");
            rule.sortOrder = rs.getInt("sort_order");
            map.put(rule.id, rule);
        });
        if (map.isEmpty()) {
            return List.of();
        }
        jdbc.query("""
                SELECT rule_id, item_code, item_name, personal_rate, base_min, base_max, sort_order
                FROM pay_social_rule_item WHERE is_active = 1
                ORDER BY sort_order, id
                """, rs -> {
            Rule rule = map.get(rs.getLong("rule_id"));
            if (rule == null) {
                return;
            }
            Item item = new Item();
            item.itemCode = rs.getString("item_code");
            item.itemName = rs.getString("item_name");
            item.personalRate = rs.getBigDecimal("personal_rate");
            item.baseMin = rs.getBigDecimal("base_min");
            item.baseMax = rs.getBigDecimal("base_max");
            rule.items.add(item);
        });
        return new ArrayList<>(map.values());
    }

    /**
     * 先按户口过滤，再看缴费基数是否落在规则区间；多条命中时优先上海。
     * 若基数落空，则按户口（优先上海）取规则，计算时再夹到该规则区间。
     */
    static Rule matchRule(String hukouType, BigDecimal base, List<Rule> rules) {
        if (rules == null || rules.isEmpty() || base == null) {
            return null;
        }
        String hukou = hukouType == null ? "" : hukouType.trim();
        List<Rule> byHukou = new ArrayList<>();
        for (Rule rule : rules) {
            if (!StringUtils.hasText(hukou) || hukou.equals(rule.hukouType)) {
                byHukou.add(rule);
            }
        }
        if (byHukou.isEmpty()) {
            byHukou = new ArrayList<>(rules);
        }
        List<Rule> inRange = new ArrayList<>();
        for (Rule rule : byHukou) {
            if (base.compareTo(rule.baseMin) >= 0 && base.compareTo(rule.baseMax) <= 0) {
                inRange.add(rule);
            }
        }
        List<Rule> candidates = inRange.isEmpty() ? byHukou : inRange;
        Rule preferred = null;
        for (Rule rule : candidates) {
            if (PREFERRED_REGION.equals(rule.regionName)) {
                preferred = rule;
                break;
            }
        }
        return preferred != null ? preferred : candidates.get(0);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal v = value;
        if (min != null && v.compareTo(min) < 0) {
            v = min;
        }
        if (max != null && v.compareTo(max) > 0) {
            v = max;
        }
        return v;
    }

    public record EmployeeSocial(long id, String hukouType, BigDecimal socialBase) {
    }

    static final class Rule {
        private long id;
        private String ruleCode;
        private String regionName;
        private String hukouType;
        private BigDecimal baseMin;
        private BigDecimal baseMax;
        private int sortOrder;
        private final List<Item> items = new ArrayList<>();
    }

    static final class Item {
        private String itemCode;
        private String itemName;
        private BigDecimal personalRate;
        private BigDecimal baseMin;
        private BigDecimal baseMax;
    }
}
