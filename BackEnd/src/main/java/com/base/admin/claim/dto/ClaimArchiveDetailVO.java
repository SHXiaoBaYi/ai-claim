package com.base.admin.claim.dto;

import com.base.admin.claim.domain.ClaimAdviceDecision;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimEvidence;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.domain.ClaimMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "理赔档案详情")
public class ClaimArchiveDetailVO {

    @Schema(description = "档案")
    private ClaimCase archive;

    @Schema(description = "对话")
    private List<ClaimMessage> messages;

    @Schema(description = "证据")
    private List<ClaimEvidence> evidences;

    @Schema(description = "建议引用的历史案例")
    private List<ClaimHistoryCase> similarCases;

    @Schema(description = "综合建议的人工处理记录")
    private List<ClaimAdviceDecision> decisions;
}
