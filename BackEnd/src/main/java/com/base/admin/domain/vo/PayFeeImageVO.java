package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "额外费用截图")
public class PayFeeImageVO {

    @Schema(description = "截图路径", example = "/uploads/payroll-fee/a.png")
    private String url;
}
