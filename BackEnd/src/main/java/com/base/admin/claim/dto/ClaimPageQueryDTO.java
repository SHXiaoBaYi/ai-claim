package com.base.admin.claim.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "理赔列表查询")
public class ClaimPageQueryDTO extends PageQuery {

    @Schema(description = "顾客姓名")
    private String customerName;

    @Schema(description = "顾客电话")
    private String customerPhone;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "档案状态")
    private String status;

    @Schema(description = "关键词，匹配姓名、电话、产品、现象、投诉内容")
    private String keyword;

    @Schema(description = "来源年份")
    private String sourceYear;
}
