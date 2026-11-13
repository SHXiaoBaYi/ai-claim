package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "免登查看的历史参照")
public class ClaimRefPageVO {

    @Schema(description = "本次投诉现象")
    private String symptom;

    @Schema(description = "本次投诉现象大类")
    private String symptomCategory;

    @Schema(description = "本次产品分组")
    private String productGroup;

    @Schema(description = "按等价度从高到低")
    private List<ClaimRefTabVO> tabs = new ArrayList<>();
}
