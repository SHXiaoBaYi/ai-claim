package com.base.admin.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "历史案例导入结果")
public class ClaimImportResultVO {

    @Schema(description = "新增条数")
    private int inserted;

    @Schema(description = "更新条数")
    private int updated;

    @Schema(description = "因手工修改而跳过的条数")
    private int skipped;

    @Schema(description = "说明")
    private String message;
}
