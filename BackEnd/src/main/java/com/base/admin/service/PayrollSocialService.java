package com.base.admin.service;

import com.base.admin.domain.dto.PaySocialRuleDTO;
import com.base.admin.domain.dto.PaySocialRuleItemDTO;
import com.base.admin.domain.vo.PaySocialRuleItemVO;
import com.base.admin.domain.vo.PaySocialRuleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollSocialService {

    private final JdbcTemplate jdbc;

    public List<PaySocialRuleVO> list() {
        Map<Long, PaySocialRuleVO> rules = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, rule_code, region_name, hukou_type, base_min, base_max, remark, sort_order
                FROM pay_social_rule
                WHERE is_active = 1
                ORDER BY sort_order, id
                """, rs -> {
            PaySocialRuleVO vo = new PaySocialRuleVO();
            vo.setId(rs.getLong("id"));
            vo.setRuleCode(rs.getString("rule_code"));
            vo.setRegionName(rs.getString("region_name"));
            vo.setHukouType(rs.getString("hukou_type"));
            vo.setBaseMin(rs.getBigDecimal("base_min"));
            vo.setBaseMax(rs.getBigDecimal("base_max"));
            vo.setRemark(rs.getString("remark"));
            vo.setSortOrder(rs.getInt("sort_order"));
            rules.put(vo.getId(), vo);
        });
        if (rules.isEmpty()) {
            return List.of();
        }
        jdbc.query("""
                SELECT id, rule_id, item_code, item_name, employer_rate, personal_rate, base_min, base_max, sort_order
                FROM pay_social_rule_item
                WHERE is_active = 1
                ORDER BY sort_order, id
                """, rs -> {
            PaySocialRuleVO rule = rules.get(rs.getLong("rule_id"));
            if (rule == null) {
                return;
            }
            PaySocialRuleItemVO item = new PaySocialRuleItemVO();
            item.setId(rs.getLong("id"));
            item.setItemCode(rs.getString("item_code"));
            item.setItemName(rs.getString("item_name"));
            item.setEmployerRate(rs.getBigDecimal("employer_rate"));
            item.setPersonalRate(rs.getBigDecimal("personal_rate"));
            item.setBaseMin(rs.getBigDecimal("base_min"));
            item.setBaseMax(rs.getBigDecimal("base_max"));
            item.setSortOrder(rs.getInt("sort_order"));
            rule.getItems().add(item);
        });
        return new ArrayList<>(rules.values());
    }

    @Transactional
    public void save(PaySocialRuleDTO dto) {
        if (dto.getBaseMax().compareTo(dto.getBaseMin()) < 0) {
            throw new BusinessException("缴费基数上限不能小于下限");
        }
        String user = currentUser();
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        int rows = jdbc.update("""
                UPDATE pay_social_rule
                SET base_min = ?, base_max = ?, remark = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, dto.getBaseMin(), dto.getBaseMax(), remark, user, dto.getId());
        if (rows == 0) {
            throw new BusinessException("社保规则不存在");
        }
        for (PaySocialRuleItemDTO item : dto.getItems()) {
            if (item.getBaseMin() != null && item.getBaseMax() != null
                    && item.getBaseMax().compareTo(item.getBaseMin()) < 0) {
                throw new BusinessException("公积金基数上限不能小于下限");
            }
            int updated = jdbc.update("""
                    UPDATE pay_social_rule_item
                    SET employer_rate = ?, personal_rate = ?, base_min = ?, base_max = ?
                    WHERE id = ? AND rule_id = ? AND is_active = 1
                    """, item.getEmployerRate(), item.getPersonalRate(), item.getBaseMin(), item.getBaseMax(),
                    item.getId(), dto.getId());
            if (updated == 0) {
                throw new BusinessException("险种不存在");
            }
        }
    }

    private static String currentUser() {
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "";
    }
}
