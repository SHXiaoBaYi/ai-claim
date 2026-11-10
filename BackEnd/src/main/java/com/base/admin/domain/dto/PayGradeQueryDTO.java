package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "职级查询")
public class PayGradeQueryDTO {

    @Schema(description = "职级名称")
    private String gradeName;

    @Schema(description = "0正常 1停用")
    private Integer status;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
