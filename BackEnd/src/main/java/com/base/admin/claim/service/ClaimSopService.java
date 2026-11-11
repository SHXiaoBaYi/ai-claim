package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimSopNode;
import com.base.admin.claim.dto.ClaimSopNodeVO;
import com.base.admin.claim.dto.ClaimSopSaveDTO;
import com.base.admin.claim.mapper.ClaimSopNodeMapper;
import com.base.admin.claim.support.ClaimSopCatalog;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimSopService {

    private final ClaimSopNodeMapper sopNodeMapper;

    public List<ClaimSopNodeVO> tree() {
        List<ClaimSopNode> nodes = all();
        Map<String, ClaimSopNodeVO> byCode = new LinkedHashMap<>();
        for (ClaimSopNode node : nodes) {
            byCode.put(node.getNodeCode(), toVo(node));
        }
        List<ClaimSopNodeVO> roots = new ArrayList<>();
        for (ClaimSopNode node : nodes) {
            ClaimSopNodeVO vo = byCode.get(node.getNodeCode());
            ClaimSopNodeVO parent = byCode.get(ClaimTexts.empty(node.getParentCode()));
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    @Transactional
    public void save(ClaimSopSaveDTO dto) {
        String type = dto.getNodeType().trim().toUpperCase();
        if (!type.equals("CATEGORY") && !type.equals("SYMPTOM") && !type.equals("ACTION")) {
            throw new BusinessException("节点类型只能是 CATEGORY、SYMPTOM 或 ACTION");
        }
        ClaimSopNode node = dto.getId() == null ? new ClaimSopNode() : sopNodeMapper.selectById(dto.getId());
        if (node == null) {
            throw new BusinessException("流程节点不存在");
        }
        node.setNodeCode(dto.getNodeCode().trim());
        node.setParentCode(ClaimTexts.empty(dto.getParentCode()));
        node.setNodeType(type);
        node.setTitle(dto.getTitle().trim());
        node.setContent(ClaimTexts.empty(dto.getContent()));
        node.setKeywords(ClaimTexts.empty(dto.getKeywords()));
        node.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        if (dto.getId() == null) {
            sopNodeMapper.insert(node);
        } else {
            sopNodeMapper.updateById(node);
        }
    }

    @Transactional
    public void delete(Long id) {
        ClaimSopNode node = sopNodeMapper.selectById(id);
        if (node == null) {
            return;
        }
        Long children = sopNodeMapper.selectCount(new LambdaQueryWrapper<ClaimSopNode>()
                .eq(ClaimSopNode::getParentCode, node.getNodeCode()));
        if (children != null && children > 0) {
            throw new BusinessException("请先删除下级节点");
        }
        node.setNodeCode(node.getNodeCode() + "-del-" + node.getId());
        sopNodeMapper.updateById(node);
        sopNodeMapper.deleteById(id);
    }

    @Transactional
    public void seedIfEmpty() {
        Long count = sopNodeMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        for (ClaimSopNode node : ClaimSopCatalog.nodes()) {
            sopNodeMapper.insert(node);
        }
    }

    /** 已入库的流程不会整表重写。只修正「坏了」被绑在蔬菜坏果上、肉品坏了对不上的关键词。 */
    @Transactional
    public void patchKeywordTraps() {
        ClaimSopNode veg = find("veg.bad");
        if (veg != null) {
            String keywords = ClaimTexts.empty(veg.getKeywords());
            if (keywords.contains("坏了") && !keywords.contains("蔬菜坏了")) {
                veg.setKeywords("坏果,蔬菜坏了,水果坏了,坏菜");
                veg.setContent("问存放方式，看是个别坏果还是整箱较多，并要照片。只用于蔬菜水果，牛羊肉坏了不要用这个分支。");
                sopNodeMapper.updateById(veg);
            }
        }
        ClaimSopNode color = find("product.color");
        if (color != null && !ClaimTexts.empty(color.getKeywords()).contains("肉坏了")) {
            String keywords = ClaimTexts.empty(color.getKeywords());
            if (keywords.isBlank()) {
                keywords = "发黑,不新鲜,变色,发暗";
            }
            color.setKeywords(keywords + ",肉坏了,变质了");
            sopNodeMapper.updateById(color);
        }
    }

    public ClaimSopNode matchSymptom(String blob) {
        String text = ClaimTexts.empty(blob);
        boolean meat = ClaimTexts.meatText(text);
        boolean produce = ClaimTexts.produceText(text);
        ClaimSopNode best = null;
        int bestScore = 0;
        for (ClaimSopNode node : all()) {
            if (!"SYMPTOM".equals(node.getNodeType()) || ClaimTexts.blank(node.getKeywords())) {
                continue;
            }
            if (node.getNodeCode().startsWith("veg.") && meat && !produce) {
                continue;
            }
            int score = 0;
            for (String keyword : node.getKeywords().split(",")) {
                String word = keyword.trim();
                if (word.length() < 1 || !text.contains(word)) {
                    continue;
                }
                if ("坏了".equals(word) && node.getNodeCode().startsWith("veg.") && !produce) {
                    continue;
                }
                score += word.length();
            }
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        if (text.contains("开化") && (text.contains("异味") || text.contains("臭") || text.contains("黏") || text.contains("粘") || text.contains("血水") || (meat && text.contains("坏了")))) {
            ClaimSopNode severe = find("logistics.severe");
            if (severe != null) {
                return severe;
            }
        }
        if (meat && !produce && ClaimTexts.spoilageText(text)) {
            if (best == null || best.getNodeCode().startsWith("veg.")) {
                ClaimSopNode color = find("product.color");
                if (color != null) {
                    return color;
                }
            }
        }
        if (produce && !meat && (text.contains("坏果") || text.contains("蔬菜坏了") || text.contains("水果坏了") || text.contains("坏菜"))) {
            if (best == null || !best.getNodeCode().startsWith("veg.")) {
                ClaimSopNode bad = find("veg.bad");
                if (bad != null) {
                    return bad;
                }
            }
        }
        return best;
    }

    public ClaimSopNode pickAction(ClaimSopNode symptom, ClaimCase claim) {
        if (symptom == null) {
            return null;
        }
        List<ClaimSopNode> actions = all().stream()
                .filter(node -> "ACTION".equals(node.getNodeType()) && symptom.getNodeCode().equals(node.getParentCode()))
                .sorted(Comparator.comparing(node -> node.getSortOrder() == null ? 0 : node.getSortOrder()))
                .toList();
        if (actions.isEmpty()) {
            return symptom;
        }
        String code = symptom.getNodeCode();
        String type = ClaimTexts.empty(claim.getCustomerType());
        String demand = ClaimTexts.empty(claim.getCustomerDemand());
        String consume = ClaimTexts.empty(claim.getConsumeSummary());
        String symptomText = ClaimTexts.empty(claim.getSymptom());
        if (code.startsWith("product.foreign")) {
            if (containsAny(demand, "索赔", "赔偿", "十倍")) {
                return byCode(actions, "product.foreign.claim");
            }
            if (containsAny(demand + symptomText, "假", "不属实", "寄回")) {
                return byCode(actions, "product.foreign.fake");
            }
            return byCode(actions, "product.foreign.no-claim");
        }
        if (code.startsWith("product.weight")) {
            if (symptomText.contains("礼盒")) {
                return byCode(actions, "product.weight.gift");
            }
            if (containsAny(symptomText + demand, "查实", "确实少", "确实缺")) {
                return byCode(actions, "product.weight.confirmed");
            }
            return byCode(actions, "product.weight.fresh");
        }
        if (code.startsWith("product.fat")) {
            if (containsAny(symptomText, "超标")) {
                return type.contains("新") ? byCode(actions, "product.fat.new") : byCode(actions, "product.fat.old");
            }
            if (containsAny(demand + symptomText, "不接受", "太肥")) {
                return type.contains("新") ? byCode(actions, "product.fat.new") : byCode(actions, "product.fat.old");
            }
            return byCode(actions, "product.fat.normal");
        }
        if (code.startsWith("product.tough")) {
            if (containsAny(consume, "每次投诉", "投诉次数")) {
                return byCode(actions, "product.tough.wool");
            }
            if (consume.contains("第一次") || symptomText.contains("第一次")) {
                return byCode(actions, "product.tough.first");
            }
            return type.contains("新") ? byCode(actions, "product.tough.new") : byCode(actions, "product.tough.repeat");
        }
        if (code.startsWith("product.smell")) {
            if (type.contains("老")) {
                return byCode(actions, "product.smell.old");
            }
            if (containsAny(consume + symptomText, "不吃羊肉", "不常吃", "第一次吃")) {
                return byCode(actions, "product.smell.new-rare");
            }
            return byCode(actions, "product.smell.new-often");
        }
        if (code.startsWith("product.bland")) {
            if (containsAny(symptomText, "怪味", "异味")) {
                return byCode(actions, "product.bland.odd");
            }
            return type.contains("新") ? byCode(actions, "product.bland.new") : byCode(actions, "product.bland.old");
        }
        if (code.startsWith("product.color")) {
            return containsAny(symptomText + consume, "顾客存放", "自己放", "储存")
                    ? byCode(actions, "product.color.customer")
                    : byCode(actions, "product.color.logistics");
        }
        if (code.startsWith("veg.bad")) {
            return containsAny(symptomText, "较多", "很多", "大部分", "整箱")
                    ? byCode(actions, "veg.bad.many")
                    : byCode(actions, "veg.bad.few");
        }
        if (code.startsWith("veg.crush")) {
            return containsAny(symptomText, "外箱", "外包装")
                    ? byCode(actions, "veg.crush.box")
                    : byCode(actions, "veg.crush.inside");
        }
        if (code.startsWith("veg.taste")) {
            return containsAny(symptomText, "近期", "很多人", "集中")
                    ? byCode(actions, "veg.taste.many")
                    : byCode(actions, "veg.taste.rare");
        }
        if (code.startsWith("logistics.light")) {
            return actions.get(0);
        }
        if (code.startsWith("logistics.severe")) {
            if (containsAny(claim.getLogisticsSummary(), "超时", "延误", "时效不正常", "超过")) {
                return byCode(actions, "logistics.severe.delay");
            }
            return type.contains("老")
                    ? byCode(actions, "logistics.severe.ontime-old")
                    : byCode(actions, "logistics.severe.ontime-new");
        }
        if (code.startsWith("logistics.lost")) {
            return containsAny(ClaimTexts.empty(claim.getLogisticsSummary()) + symptomText, "72", "丢失", "一直没到")
                    ? byCode(actions, "logistics.lost.over")
                    : byCode(actions, "logistics.lost.normal");
        }
        if (code.startsWith("service.origin")) {
            return containsAny(symptomText + demand, "没收到", "未收到")
                    ? byCode(actions, "service.origin.unreceived")
                    : byCode(actions, "service.origin.received");
        }
        if (type.contains("老")) {
            return actions.stream().filter(node -> node.getTitle().contains("老")).findFirst().orElse(actions.get(0));
        }
        if (type.contains("新")) {
            return actions.stream().filter(node -> node.getTitle().contains("新")).findFirst().orElse(actions.get(0));
        }
        return actions.get(0);
    }

    public ClaimSopNode find(String code) {
        return all().stream().filter(node -> code.equals(node.getNodeCode())).findFirst().orElse(null);
    }

    private ClaimSopNode byCode(List<ClaimSopNode> actions, String code) {
        return actions.stream().filter(node -> code.equals(node.getNodeCode())).findFirst().orElse(actions.get(0));
    }

    private boolean containsAny(String text, String... words) {
        String value = ClaimTexts.empty(text);
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<ClaimSopNode> all() {
        return sopNodeMapper.selectList(new LambdaQueryWrapper<ClaimSopNode>()
                .orderByAsc(ClaimSopNode::getSortOrder)
                .orderByAsc(ClaimSopNode::getId));
    }

    private ClaimSopNodeVO toVo(ClaimSopNode node) {
        ClaimSopNodeVO vo = new ClaimSopNodeVO();
        vo.setId(node.getId());
        vo.setNodeCode(node.getNodeCode());
        vo.setParentCode(node.getParentCode());
        vo.setNodeType(node.getNodeType());
        vo.setTitle(node.getTitle());
        vo.setContent(node.getContent());
        vo.setKeywords(node.getKeywords());
        vo.setSortOrder(node.getSortOrder());
        return vo;
    }
}
