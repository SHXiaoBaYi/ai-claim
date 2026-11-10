package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "算薪岗位")
public class PayPostVO {

    @Schema(description = "岗位ID")
    private Long id;

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "0正常 1停用")
    private Integer status;
}
