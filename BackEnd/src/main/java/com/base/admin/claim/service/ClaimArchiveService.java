package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.claim.domain.ClaimAdviceDecision;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimEvidence;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.domain.ClaimMessage;
import com.base.admin.claim.dto.ClaimArchiveDetailVO;
import com.base.admin.claim.dto.ClaimArchiveUpdateDTO;
import com.base.admin.claim.dto.ClaimPageQueryDTO;
import com.base.admin.claim.mapper.ClaimAdviceDecisionMapper;
import com.base.admin.claim.mapper.ClaimCaseMapper;
import com.base.admin.claim.mapper.ClaimEvidenceMapper;
import com.base.admin.claim.mapper.ClaimHistoryCaseMapper;
import com.base.admin.claim.mapper.ClaimMessageMapper;
import com.base.admin.claim.support.ClaimProductGroup;
import com.base.admin.claim.support.ClaimSymptomCategory;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.common.PageResult;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimArchiveService {

    private final ClaimCaseMapper caseMapper;
    private final ClaimAdviceDecisionMapper decisionMapper;
    private final ClaimMessageMapper messageMapper;
    private final ClaimEvidenceMapper evidenceMapper;
    private final ClaimHistoryCaseMapper historyMapper;
    private final ClaimFileStorage fileStorage;
    private final ClaimAdviceService adviceService;

    public PageResult<ClaimCase> page(ClaimPageQueryDTO query) {
        LambdaQueryWrapper<ClaimCase> wrapper = new LambdaQueryWrapper<ClaimCase>()
                .like(!ClaimTexts.blank(query.getCustomerName()), ClaimCase::getCustomerName, query.getCustomerName())
                .like(!ClaimTexts.blank(query.getCustomerPhone()), ClaimCase::getCustomerPhone, query.getCustomerPhone())
                .like(!ClaimTexts.blank(query.getProductName()), ClaimCase::getProductName, query.getProductName())
                .eq(!ClaimTexts.blank(query.getStatus()), ClaimCase::getStatus, query.getStatus())
                .orderByDesc(ClaimCase::getId);
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        Page<ClaimCase> page = caseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public ClaimArchiveDetailVO detail(Long id) {
        ClaimCase archive = require(id);
        ClaimArchiveDetailVO vo = new ClaimArchiveDetailVO();
        vo.setArchive(archive);
        vo.setMessages(messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, id)
                .orderByAsc(ClaimMessage::getId)));
        vo.setEvidences(evidenceMapper.selectList(new LambdaQueryWrapper<ClaimEvidence>()
                .eq(ClaimEvidence::getCaseId, id)
                .orderByAsc(ClaimEvidence::getId)));
        vo.setSimilarCases(loadSimilar(archive.getSimilarCaseIds()));
        vo.setDecisions(decisionMapper.selectList(new LambdaQueryWrapper<ClaimAdviceDecision>()
                .eq(ClaimAdviceDecision::getCaseId, id)
                .orderByDesc(ClaimAdviceDecision::getId)));
        return vo;
    }

    public ClaimEvidence evidence(Long id) {
        ClaimEvidence evidence = evidenceMapper.selectById(id);
        if (evidence == null) {
            throw new BusinessException("证据不存在");
        }
        return evidence;
    }

    @Transactional
    public void update(ClaimArchiveUpdateDTO dto) {
        ClaimCase archive = require(dto.getId());
        boolean textChanged = changed(archive.getSymptom(), dto.getSymptom())
                || changed(archive.getProductName(), dto.getProductName())
                || changed(archive.getProductCategory(), dto.getProductCategory())
                || changed(archive.getLogisticsSummary(), dto.getLogisticsSummary());
        String previousCategory = archive.getSymptomCategory();
        String previousGroup = archive.getProductGroup();
        archive.setCustomerName(ClaimTexts.empty(dto.getCustomerName()));
        archive.setCustomerPhone(ClaimTexts.empty(dto.getCustomerPhone()));
        archive.setCustomerAddress(ClaimTexts.empty(dto.getCustomerAddress()));
        archive.setMemberNo(ClaimTexts.empty(dto.getMemberNo()));
        archive.setCustomerType(ClaimTexts.empty(dto.getCustomerType()));
        archive.setConsumeSummary(ClaimTexts.empty(dto.getConsumeSummary()));
        archive.setProductCategory(ClaimTexts.empty(dto.getProductCategory()));
        archive.setProductGroup(ClaimSymptomCategory.choose(dto.getProductGroup(), previousGroup, textChanged,
                ClaimProductGroup.of(dto.getProductName(), dto.getProductCategory(), dto.getSymptom())));
        archive.setProductName(ClaimTexts.empty(dto.getProductName()));
        archive.setProductSpec(ClaimTexts.empty(dto.getProductSpec()));
        archive.setStoreName(ClaimTexts.empty(dto.getStoreName()));
        archive.setPurchaseDate(ClaimTexts.empty(dto.getPurchaseDate()));
        archive.setOrderNo(ClaimTexts.empty(dto.getOrderNo()));
        archive.setLogisticsNo(ClaimTexts.empty(dto.getLogisticsNo()));
        archive.setLogisticsSummary(ClaimTexts.empty(dto.getLogisticsSummary()));
        archive.setSymptom(ClaimTexts.empty(dto.getSymptom()));
        archive.setSymptomCategory(ClaimSymptomCategory.choose(dto.getSymptomCategory(), previousCategory, textChanged,
                ClaimSymptomCategory.classify(dto.getSymptom(), dto.getProductName(), dto.getProductCategory(),
                        "", dto.getLogisticsSummary())));
        String previousAdvice = archive.getRecommendation();
        archive.setCookingMethod(ClaimTexts.empty(dto.getCookingMethod()));
        archive.setCustomerDemand(ClaimTexts.empty(dto.getCustomerDemand()));
        archive.setRecommendation(ClaimTexts.empty(dto.getRecommendation()));
        archive.setRecommendationBasis(ClaimTexts.empty(dto.getRecommendationBasis()));
        adviceService.recordManual(archive, previousAdvice, archive.getRecommendation(), SecurityUtils.getCurrentUsername());
        caseMapper.updateById(archive);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        List<ClaimMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ClaimMessage>()
                .eq(ClaimMessage::getCaseId, id));
        for (ClaimMessage message : messages) {
            messageMapper.deleteById(message.getId());
        }
        List<ClaimEvidence> evidences = evidenceMapper.selectList(new LambdaQueryWrapper<ClaimEvidence>()
                .eq(ClaimEvidence::getCaseId, id));
        for (ClaimEvidence evidence : evidences) {
            evidenceMapper.deleteById(evidence.getId());
        }
        fileStorage.deleteCaseDir(id);
        caseMapper.deleteById(id);
    }

    /** 只补空着的现象大类和产品分组。档案从收集开始就带着这两项，后面才能按类积累。 */
    public void fillSymptomCategories() {
        List<ClaimCase> rows = caseMapper.selectList(new LambdaQueryWrapper<ClaimCase>()
                .and(wrapper -> wrapper.isNull(ClaimCase::getSymptomCategory)
                        .or().eq(ClaimCase::getSymptomCategory, "")
                        .or().isNull(ClaimCase::getProductGroup)
                        .or().eq(ClaimCase::getProductGroup, "")));
        int updated = 0;
        for (ClaimCase row : rows) {
            if (ClaimTexts.blank(row.getSymptom()) && ClaimTexts.blank(row.getProductName())) {
                continue;
            }
            boolean changed = false;
            if (ClaimTexts.blank(row.getSymptomCategory())) {
                row.setSymptomCategory(ClaimSymptomCategory.classify(row.getSymptom(), row.getProductName(),
                        row.getProductCategory(), "", row.getLogisticsSummary()));
                changed = true;
            }
            if (ClaimTexts.blank(row.getProductGroup())) {
                row.setProductGroup(ClaimProductGroup.of(row.getProductName(), row.getProductCategory(), row.getSymptom()));
                changed = true;
            }
            if (changed) {
                caseMapper.updateById(row);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("已补理赔档案现象大类和产品分组 {} 条", updated);
        }
    }

    private boolean changed(String previous, String submitted) {
        return !ClaimTexts.empty(previous).equals(ClaimTexts.empty(submitted));
    }

    public ClaimCase require(Long id) {
        ClaimCase archive = caseMapper.selectById(id);
        if (archive == null) {
            throw new BusinessException("理赔档案不存在");
        }
        return archive;
    }

    private List<ClaimHistoryCase> loadSimilar(String ids) {
        if (ClaimTexts.blank(ids)) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String item : ids.split(",")) {
            try {
                parsed.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略无效 ID
            }
        }
        if (parsed.isEmpty()) {
            return List.of();
        }
        List<ClaimHistoryCase> rows = historyMapper.selectList(new LambdaQueryWrapper<ClaimHistoryCase>()
                .in(ClaimHistoryCase::getId, parsed));
        rows.sort((a, b) -> Integer.compare(parsed.indexOf(a.getId()), parsed.indexOf(b.getId())));
        return rows;
    }
}
