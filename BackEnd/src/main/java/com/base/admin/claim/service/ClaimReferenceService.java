package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.dto.ClaimRefCaseVO;
import com.base.admin.claim.dto.ClaimRefPageVO;
import com.base.admin.claim.dto.ClaimRefTabVO;
import com.base.admin.claim.mapper.ClaimCaseMapper;
import com.base.admin.claim.mapper.ClaimHistoryCaseMapper;
import com.base.admin.claim.support.ClaimTexts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimReferenceService {

    private final ClaimCaseMapper caseMapper;
    private final ClaimHistoryCaseMapper historyMapper;
    private final ObjectMapper objectMapper;

    public ClaimRefPageVO open(String token) {
        ClaimRefPageVO page = new ClaimRefPageVO();
        if (ClaimTexts.blank(token)) {
            return page;
        }
        ClaimCase claim = caseMapper.selectOne(new LambdaQueryWrapper<ClaimCase>()
                .eq(ClaimCase::getReferenceToken, token.trim())
                .last("limit 1"));
        if (claim == null || ClaimTexts.blank(claim.getReferenceView())) {
            return page;
        }
        page.setSymptom(ClaimTexts.empty(claim.getSymptom()));
        page.setSymptomCategory(ClaimTexts.empty(claim.getSymptomCategory()));
        page.setProductGroup(ClaimTexts.empty(claim.getProductGroup()));
        List<ClaimConversationService.RefMark> marks = readMarks(claim.getReferenceView());
        if (marks.isEmpty()) {
            return page;
        }
        List<Long> historyIds = new ArrayList<>();
        List<Long> archiveIds = new ArrayList<>();
        for (ClaimConversationService.RefMark mark : marks) {
            if ("archive".equals(mark.getSource())) {
                archiveIds.add(mark.getId());
            } else {
                historyIds.add(mark.getId());
            }
        }
        Map<Long, ClaimHistoryCase> history = new LinkedHashMap<>();
        if (!historyIds.isEmpty()) {
            for (ClaimHistoryCase row : historyMapper.selectBatchIds(historyIds)) {
                if (row != null && row.getId() != null) {
                    history.put(row.getId(), row);
                }
            }
        }
        Map<Long, ClaimCase> archives = new LinkedHashMap<>();
        if (!archiveIds.isEmpty()) {
            for (ClaimCase row : caseMapper.selectBatchIds(archiveIds)) {
                if (row != null && row.getId() != null) {
                    archives.put(row.getId(), row);
                }
            }
        }
        Map<String, ClaimRefTabVO> tabs = new LinkedHashMap<>();
        marks.stream()
                .sorted(Comparator.comparingDouble(ClaimConversationService.RefMark::getScore).reversed())
                .forEach(mark -> {
                    ClaimRefCaseVO item = "archive".equals(mark.getSource())
                            ? toArchive(archives.get(mark.getId()), mark)
                            : toCase(history.get(mark.getId()), mark);
                    if (item == null) {
                        return;
                    }
                    String score = item.getScore();
                    ClaimRefTabVO tab = tabs.computeIfAbsent(score, key -> {
                        ClaimRefTabVO created = new ClaimRefTabVO();
                        created.setScore(key);
                        return created;
                    });
                    tab.getCases().add(item);
                });
        page.setTabs(new ArrayList<>(tabs.values()));
        return page;
    }

    private List<ClaimConversationService.RefMark> readMarks(String raw) {
        try {
            List<ClaimConversationService.RefMark> marks = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return marks == null ? List.of() : marks;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ClaimRefCaseVO toCase(ClaimHistoryCase row, ClaimConversationService.RefMark mark) {
        if (row == null) {
            return null;
        }
        ClaimRefCaseVO vo = new ClaimRefCaseVO();
        vo.setId(row.getId());
        vo.setScore(String.format(java.util.Locale.ROOT, "%.2f", mark.getScore()));
        vo.setKind(ClaimTexts.empty(mark.getKind()));
        vo.setSource("history");
        vo.setComplaintDate(ClaimTexts.empty(row.getComplaintDate()));
        vo.setCustomerName(ClaimTexts.empty(row.getCustomerName()));
        vo.setProductName(ClaimTexts.empty(row.getProductName()));
        vo.setSymptomBrief(ClaimTexts.empty(row.getSymptomBrief()));
        vo.setClaimOpinion(ClaimTexts.empty(row.getClaimOpinion()));
        vo.setJudgment(ClaimTexts.empty(row.getJudgment()));
        vo.setResultText(ClaimTexts.empty(row.getResultText()));
        return vo;
    }

    private ClaimRefCaseVO toArchive(ClaimCase row, ClaimConversationService.RefMark mark) {
        if (row == null) {
            return null;
        }
        ClaimRefCaseVO vo = new ClaimRefCaseVO();
        vo.setId(row.getId());
        vo.setScore(String.format(java.util.Locale.ROOT, "%.2f", mark.getScore()));
        vo.setKind(ClaimTexts.empty(mark.getKind()));
        vo.setSource("archive");
        vo.setComplaintDate(ClaimTexts.empty(row.getPurchaseDate()));
        vo.setCustomerName(ClaimTexts.empty(row.getCustomerName()));
        vo.setProductName(ClaimTexts.empty(row.getProductName()));
        vo.setSymptomBrief(ClaimTexts.empty(row.getSymptom()));
        vo.setClaimOpinion(ClaimTexts.empty(row.getRecommendation()));
        return vo;
    }
}
