package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "按等价度分页签")
public class ClaimRefTabVO {

    @Schema(description = "等价度，保留两位")
    private String score;

    @Schema(description = "该等价度下的案例")
    private List<ClaimRefCaseVO> cases = new ArrayList<>();
}
