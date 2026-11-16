package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "消协法适用依据")
public class ClaimLawVO {

    @NotBlank(message = "请填写消协法内容")
    @Schema(description = "消协法全文")
    private String text;
}
