package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "算薪员工")
public class PayEmployeeVO {

    @Schema(description = "员工ID")
    private Long id;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "岗位ID")
    private Long postId;

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "职级ID")
    private Long gradeId;

    @Schema(description = "职级名称")
    private String gradeName;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "入职时间", example = "2026-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    @Schema(description = "工资标准（底薪构成合计）")
    private BigDecimal salaryStandard;

    @Schema(description = "岗位工资")
    private BigDecimal amtPost;

    @Schema(description = "全勤工资")
    private BigDecimal amtFullAttendance;

    @Schema(description = "社会保险补贴")
    private BigDecimal amtSocialSubsidy;

    @Schema(description = "公积金补贴")
    private BigDecimal amtHousingSubsidy;

    @Schema(description = "交通通讯补助")
    private BigDecimal amtTransport;

    @Schema(description = "竞业费")
    private BigDecimal amtNoncompete;

    @Schema(description = "洗理费")
    private BigDecimal amtHygiene;

    @Schema(description = "固定加班费")
    private BigDecimal amtFixedOvertime;

    @Schema(description = "工号")
    private String employeeNo;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "银行卡号")
    private String bankAccount;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "1在职 0离职")
    private Integer jobStatus;

    @Schema(description = "户口性质：城镇/农村")
    private String hukouType;

    @Schema(description = "社保缴费基数")
    private BigDecimal socialBase;

    @Schema(description = "个人社保账号")
    private String socialAccount;
}
