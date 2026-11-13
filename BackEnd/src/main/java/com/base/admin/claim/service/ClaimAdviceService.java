package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.claim.ClaimDingTalkBridge;
import com.base.admin.claim.domain.ClaimAdviceDecision;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimMessage;
import com.base.admin.claim.dto.ClaimAdviceEditDTO;
import com.base.admin.claim.dto.ClaimAdvicePageVO;
import com.base.admin.claim.mapper.ClaimAdviceDecisionMapper;
import com.base.admin.claim.mapper.ClaimCaseMapper;
import com.base.admin.claim.mapper.ClaimMessageMapper;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAdviceService {

    private final ClaimCaseMapper caseMapper;
    private final ClaimAdviceDecisionMapper decisionMapper;
    private final ClaimMessageMapper messageMapper;
    private final ClaimConversationService conversationService;
    private final ClaimDingTalkBridge dingTalkBridge;

    public ClaimAdvicePageVO open(String token) {
        return pageOf(require(token));
    }

    @Transactional
    public ClaimAdvicePageVO adopt(String token) {
        ClaimCase claim = require(token);
        String advice = claim.getRecommendation();
        record(claim, ClaimAdviceDecision.ADOPTED, advice, advice, claim.getSenderName());
        note(claim.getId(), "已完全采纳综合建议");
        caseMapper.updateById(claim);
        return pageOf(claim);
    }

    @Transactional
    public ClaimAdvicePageVO redo(String token) {
        ClaimCase claim = require(token);
        String rejected = claim.getRecommendation();
        record(claim, ClaimAdviceDecision.REDO, rejected, "", claim.getSenderName());
        note(claim.getId(), "不满意这条建议，已要求重新出");
        caseMapper.updateById(claim);
        conversationService.recommendAgain(claim.getId());
        return pageOf(caseMapper.selectById(claim.getId()));
    }

    @Transactional
    public ClaimAdvicePageVO partial(String token, ClaimAdviceEditDTO dto) {
        ClaimCase claim = require(token);
        ClaimAdviceEditDTO edit = dto == null ? new ClaimAdviceEditDTO() : dto;
        String next = ClaimTexts.advice(edit.getNature(), edit.getActions(), edit.getPlan());
        if (ClaimTexts.blank(edit.getNature()) && ClaimTexts.blank(edit.getActions()) && ClaimTexts.blank(edit.getPlan())) {
            throw new BusinessException("请至少保留一段建议");
        }
        String previous = claim.getRecommendation();
        record(claim, ClaimAdviceDecision.PARTIAL, previous, next, claim.getSenderName());
        claim.setRecommendation(next);
        note(claim.getId(), "已部分修改并采纳建议：\n" + next);
        caseMapper.updateById(claim);
        return pageOf(claim);
    }

    /** 档案里手工改了建议时记一笔，后面出建议能参照。 */
    public void recordManual(ClaimCase claim, String previous, String next, String decider) {
        if (claim == null || ClaimTexts.blank(next) || ClaimTexts.empty(previous).equals(ClaimTexts.empty(next))) {
            return;
        }
        record(claim, ClaimAdviceDecision.PARTIAL, previous, next, decider);
    }

    public void push(String token) {
        try {
            ClaimCase claim = caseMapper.selectOne(new LambdaQueryWrapper<ClaimCase>()
                    .eq(ClaimCase::getReferenceToken, ClaimTexts.empty(token))
                    .last("limit 1"));
            dingTalkBridge.pushAdvice(claim);
        } catch (Exception ex) {
            log.warn("重出建议后回推钉钉失败: {}", ex.getMessage());
        }
    }

    private void record(ClaimCase claim, String decision, String original, String finalAdvice, String decider) {
        ClaimAdviceDecision row = new ClaimAdviceDecision();
        row.setCaseId(claim.getId());
        row.setDecision(decision);
        row.setOriginalAdvice(ClaimTexts.empty(original));
        row.setFinalAdvice(ClaimTexts.empty(finalAdvice));
        row.setSymptomCategory(ClaimTexts.empty(claim.getSymptomCategory()));
        row.setProductGroup(ClaimTexts.empty(claim.getProductGroup()));
        String name = ClaimTexts.empty(decider);
        if (name.isBlank()) {
            name = ClaimTexts.empty(claim.getSenderName());
        }
        if (name.isBlank()) {
            name = "人工";
        }
        row.setDeciderName(ClaimTexts.clip(name, 64));
        decisionMapper.insert(row);
        claim.setAdviceDecision(decision);
    }

    private void note(Long caseId, String content) {
        ClaimMessage message = new ClaimMessage();
        message.setCaseId(caseId);
        message.setRoleName("user");
        message.setMsgType("text");
        message.setContent(content);
        messageMapper.insert(message);
    }

    private ClaimAdvicePageVO pageOf(ClaimCase claim) {
        ClaimAdvicePageVO vo = new ClaimAdvicePageVO();
        vo.setCustomerName(claim.getCustomerName());
        vo.setProductName(claim.getProductName());
        vo.setSymptom(claim.getSymptom());
        vo.setSymptomCategory(claim.getSymptomCategory());
        vo.setProductGroup(claim.getProductGroup());
        vo.setRecommendation(claim.getRecommendation());
        vo.setDecision(claim.getAdviceDecision());
        vo.setHasRefs(!ClaimTexts.blank(claim.getReferenceView()));
        return vo;
    }

    private ClaimCase require(String token) {
        if (ClaimTexts.blank(token)) {
            throw new BusinessException("链接无效");
        }
        ClaimCase claim = caseMapper.selectOne(new LambdaQueryWrapper<ClaimCase>()
                .eq(ClaimCase::getReferenceToken, token.trim())
                .last("limit 1"));
        if (claim == null || ClaimTexts.blank(claim.getRecommendation())) {
            throw new BusinessException("这条建议不存在");
        }
        return claim;
    }
}
