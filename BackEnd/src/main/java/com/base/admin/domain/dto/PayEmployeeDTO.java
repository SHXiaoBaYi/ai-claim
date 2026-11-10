package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "算薪员工")
public class PayEmployeeDTO {

    @Schema(description = "员工ID，新增时为空")
    private Long id;

    @NotNull(message = "请选择部门")
    @Schema(description = "部门ID")
    private Long deptId;

    @NotNull(message = "请选择岗位")
    @Schema(description = "岗位ID")
    private Long postId;

    @NotNull(message = "请选择职级")
    @Schema(description = "职级ID")
    private Long gradeId;

    @NotBlank(message = "请填写姓名")
    @Size(max = 50, message = "姓名不能超过50字")
    @Schema(description = "姓名")
    private String employeeName;

    @NotNull(message = "请选择入职时间")
    @Schema(description = "入职时间", example = "2026-01-01")
    private LocalDate hireDate;

    @NotNull(message = "请填写岗位工资")
    @DecimalMin(value = "0", message = "岗位工资不能为负数")
    @Schema(description = "岗位工资", example = "5000")
    private BigDecimal amtPost;

    @NotNull(message = "请填写全勤工资")
    @DecimalMin(value = "0", message = "全勤工资不能为负数")
    @Schema(description = "全勤工资", example = "200")
    private BigDecimal amtFullAttendance;

    @NotNull(message = "请填写社会保险补贴")
    @DecimalMin(value = "0", message = "社会保险补贴不能为负数")
    @Schema(description = "社会保险补贴", example = "800")
    private BigDecimal amtSocialSubsidy;

    @NotNull(message = "请填写公积金补贴")
    @DecimalMin(value = "0", message = "公积金补贴不能为负数")
    @Schema(description = "公积金补贴", example = "500")
    private BigDecimal amtHousingSubsidy;

    @NotNull(message = "请填写交通通讯补助")
    @DecimalMin(value = "0", message = "交通通讯补助不能为负数")
    @Schema(description = "交通通讯补助", example = "300")
    private BigDecimal amtTransport;

    @NotNull(message = "请填写竞业费")
    @DecimalMin(value = "0", message = "竞业费不能为负数")
    @Schema(description = "竞业费", example = "0")
    private BigDecimal amtNoncompete;

    @NotNull(message = "请填写洗理费")
    @DecimalMin(value = "0", message = "洗理费不能为负数")
    @Schema(description = "洗理费", example = "50")
    private BigDecimal amtHygiene;

    @NotNull(message = "请填写固定加班费")
    @DecimalMin(value = "0", message = "固定加班费不能为负数")
    @Schema(description = "固定加班费", example = "0")
    private BigDecimal amtFixedOvertime;

    @Size(max = 32, message = "工号不能超过32字")
    @Schema(description = "工号")
    private String employeeNo;

    @Size(max = 20, message = "手机号不能超过20字")
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 32, message = "身份证号不能超过32字")
    @Schema(description = "身份证号")
    private String idCard;

    @Size(max = 40, message = "银行卡号不能超过40字")
    @Schema(description = "银行卡号")
    private String bankAccount;

    @Size(max = 80, message = "开户行不能超过80字")
    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "1在职 0离职", example = "1")
    private Integer jobStatus;

    @Size(max = 10, message = "户口性质不能超过10字")
    @Schema(description = "户口性质：城镇/农村", example = "城镇")
    private String hukouType;

    @DecimalMin(value = "0", message = "缴费基数不能为负数")
    @Schema(description = "社保缴费基数", example = "7546")
    private BigDecimal socialBase;

    @Size(max = 40, message = "个人社保账号不能超过40字")
    @Schema(description = "个人社保账号")
    private String socialAccount;
}
