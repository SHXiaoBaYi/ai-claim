package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "算薪职级")
public class PayGradeVO {

    @Schema(description = "职级ID")
    private Long id;

    @Schema(description = "职级名称")
    private String gradeName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "0正常 1停用")
    private Integer status;
}
