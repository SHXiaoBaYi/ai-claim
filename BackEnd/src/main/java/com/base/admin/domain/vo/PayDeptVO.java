package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "算薪部门")
public class PayDeptVO {

    @Schema(description = "部门ID")
    private Long id;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "0正常 1停用")
    private Integer status;
}
