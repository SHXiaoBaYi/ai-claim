package com.base.admin.service;

import com.base.admin.common.PayRewardKinds;
import com.base.admin.domain.vo.PayWageExportJobVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.PayrollDingTalkPullService.PeriodWindow;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollWageExportService {

    private static final String COMPANY = "小巴依（上海）供应链管理有限公司";
    private static final String SHEET_NAME = "工资";
    private static final int HEADER_ROWS = 3;
    private static final int COL_COUNT = 57;

    private static final String TAX_CATEGORY = "个税";

    private final JdbcTemplate jdbc;
    private final PayrollDingTalkPullService pullService;
    private final PayrollSocialCalcService socialCalcService;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "wage-export");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public PayWageExportJobVO start(String period) {
        PeriodWindow window = pullService.parsePeriod(period);
        purgeExpired();
        String jobId = UUID.randomUUID().toString().replace("-", "");
        Job job = new Job(jobId, window.period());
        job.status = "pending";
        job.percent = 0;
        job.message = "排队中";
        job.fileName = "员工工资明细-" + window.period() + ".xlsx";
        jobs.put(jobId, job);
        executor.execute(() -> run(job, window));
        return toVo(job);
    }

    public PayWageExportJobVO progress(String jobId) {
        Job job = requireJob(jobId);
        return toVo(job);
    }

    public void writeFile(String jobId, OutputStream out) throws IOException {
        Job job = requireJob(jobId);
        if (!"done".equals(job.status) || job.file == null || !Files.isRegularFile(job.file)) {
            throw new BusinessException("工资明细尚未生成完成");
        }
        Files.copy(job.file, out);
    }

    private void run(Job job, PeriodWindow window) {
        job.status = "running";
        job.percent = 2;
        job.message = "读取员工与算薪数据";
        Path temp = null;
        try {
            List<Emp> employees = loadEmployees();
            if (employees.isEmpty()) {
                throw new BusinessException("没有在职员工，无法生成工资明细");
            }
            Map<Long, Att> attendance = loadAttendance(window.period());
            Map<Long, Map<String, Integer>> rewards = loadRewardCounts(window.period());
            Map<Long, BigDecimal> rewardAmount = loadRewardAmount(window.period());
            Map<Long, BigDecimal> coeff = loadPerformance(window.period());
            Map<Long, BigDecimal> feeAdd = loadFees(window.period(), "add", null);
            Map<Long, BigDecimal> feeDeduct = loadFees(window.period(), "deduct", TAX_CATEGORY);
            Map<Long, BigDecimal> feeTax = loadFeesByCategory(window.period(), TAX_CATEGORY);
            Map<Long, LateRule> lateRules = loadLateRules();
            List<PayrollSocialCalcService.EmployeeSocial> socialInputs = employees.stream()
                    .map(e -> new PayrollSocialCalcService.EmployeeSocial(e.id, e.hukouType, e.socialBase))
                    .toList();
            Map<Long, PayrollSocialCalcService.PersonalDeduct> socialDeduct =
                    socialCalcService.calcForEmployees(socialInputs);
            int shouldAttend = loadShouldAttend(window.start().getYear(), window.start().getMonthValue());
            job.percent = 8;
            job.message = "加载工资表模版";

            try (InputStream in = new ClassPathResource("templates/wage-sheet.xlsx").getInputStream();
                 Workbook workbook = new XSSFWorkbook(in)) {
                Sheet sheet = workbook.getSheet(SHEET_NAME);
                if (sheet == null) {
                    throw new BusinessException("模版缺少「工资」工作表");
                }
                // 导出只保留「工资」sheet
                for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                    if (!SHEET_NAME.equals(workbook.getSheetName(i))) {
                        workbook.removeSheetAt(i);
                    }
                }
                prepareSheet(sheet, window);
                CellStyle dataStyle = dataStyle(workbook);
                CellStyle moneyStyle = moneyStyle(workbook);
                CellStyle pctStyle = pctStyle(workbook);

                int total = employees.size();
                int rowIndex = HEADER_ROWS;
                BigDecimal[] sums = new BigDecimal[COL_COUNT];
                for (int i = 0; i < COL_COUNT; i++) {
                    sums[i] = BigDecimal.ZERO;
                }
                int sumAttendShould = 0;
                int sumAttendActual = 0;

                for (int i = 0; i < employees.size(); i++) {
                    Emp emp = employees.get(i);
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        row = sheet.createRow(rowIndex);
                    } else {
                        clearRow(row);
                    }
                    Line line = buildLine(emp, i + 1, shouldAttend, attendance.get(emp.id),
                            rewards.getOrDefault(emp.id, Map.of()), rewardAmount.getOrDefault(emp.id, BigDecimal.ZERO),
                            coeff.getOrDefault(emp.id, BigDecimal.ONE),
                            feeAdd.getOrDefault(emp.id, BigDecimal.ZERO),
                            feeDeduct.getOrDefault(emp.id, BigDecimal.ZERO),
                            feeTax.getOrDefault(emp.id, BigDecimal.ZERO),
                            lateRules.get(emp.gradeId),
                            socialDeduct.getOrDefault(emp.id, PayrollSocialCalcService.PersonalDeduct.zero()));
                    writeLine(row, line, dataStyle, moneyStyle, pctStyle);
                    accumulate(sums, line);
                    sumAttendShould += line.shouldAttend;
                    sumAttendActual += line.actualAttend;
                    rowIndex++;
                    int percent = 10 + (int) ((i + 1) * 85.0 / total);
                    job.percent = Math.min(95, percent);
                    job.message = "正在写入 " + (i + 1) + "/" + total + "：" + emp.name;
                }

                Row summary = sheet.getRow(rowIndex);
                if (summary == null) {
                    summary = sheet.createRow(rowIndex);
                } else {
                    clearRow(summary);
                }
                writeSummary(summary, sums, sumAttendShould, sumAttendActual, employees.size(), dataStyle, moneyStyle, pctStyle);

                // drop leftover sample rows below summary
                for (int r = sheet.getLastRowNum(); r > rowIndex; r--) {
                    Row old = sheet.getRow(r);
                    if (old != null) {
                        sheet.removeRow(old);
                    }
                }

                temp = Files.createTempFile("wage-" + window.period() + "-", ".xlsx");
                try (OutputStream out = Files.newOutputStream(temp)) {
                    workbook.write(out);
                }
            }

            job.file = temp;
            job.percent = 100;
            job.status = "done";
            job.message = "已生成 " + employees.size() + " 人，可下载";
            job.expireAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        } catch (Exception ex) {
            log.error("生成工资明细失败 period={}", window.period(), ex);
            job.status = "failed";
            job.percent = 100;
            job.message = ex instanceof BusinessException ? ex.getMessage() : "生成失败：" + ex.getMessage();
            job.expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private void prepareSheet(Sheet sheet, PeriodWindow window) {
        // remove merges that only belong to old sample/summary rows
        List<CellRangeAddress> keep = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() < HEADER_ROWS) {
                keep.add(region);
            }
        }
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            sheet.removeMergedRegion(i);
        }
        for (CellRangeAddress region : keep) {
            sheet.addMergedRegion(region);
        }
        for (int r = sheet.getLastRowNum(); r >= HEADER_ROWS; r--) {
            Row row = sheet.getRow(r);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
        Row title = sheet.getRow(0);
        if (title == null) {
            title = sheet.createRow(0);
        }
        Cell titleCell = title.getCell(0);
        if (titleCell == null) {
            titleCell = title.createCell(0);
        }
        LocalDate start = window.start();
        LocalDate end = window.end();
        titleCell.setCellValue(COMPANY + start.getYear() + "年" + start.getMonthValue() + "月员工工资总表（"
                + start.getYear() + "." + start.getMonthValue() + "." + start.getDayOfMonth() + "-"
                + end.getMonthValue() + "." + end.getDayOfMonth() + "）");

        Row meta = sheet.getRow(1);
        if (meta != null) {
            Cell a2 = meta.getCell(0);
            if (a2 == null) {
                a2 = meta.createCell(0);
            }
            if (a2.getCellType() != CellType.NUMERIC) {
                a2.setCellValue(21.75);
            }
        }
    }

    private Line buildLine(Emp emp, int seq, int shouldAttend, Att att, Map<String, Integer> rewardCount,
                           BigDecimal rewardAmt, BigDecimal coefficient, BigDecimal feeAdd, BigDecimal feeDeduct,
                           BigDecimal feeTax, LateRule lateRule, PayrollSocialCalcService.PersonalDeduct social) {
        Line line = new Line();
        line.seq = seq;
        line.dept = emp.dept;
        line.hireDate = emp.hireDate == null ? "" : emp.hireDate.format(DateTimeFormatter.ofPattern("yyyy.M.d"));
        line.name = emp.name;
        line.post = emp.post;
        line.shouldAttend = Math.max(shouldAttend, 0);
        int personal1 = att == null ? 0 : att.personal1;
        int personal2 = att == null ? 0 : att.personal2;
        int absent = att == null ? 0 : att.absent;
        int lateEarly = att == null ? 0 : att.lateEarly;
        int missing = att == null ? 0 : att.missing;
        BigDecimal annual = att == null ? BigDecimal.ZERO : att.annual;
        BigDecimal special = att == null ? BigDecimal.ZERO : att.special;
        BigDecimal actualRest = att == null ? BigDecimal.ZERO : att.actualRest;
        BigDecimal remaining = att == null ? BigDecimal.ZERO : att.remaining;
        line.personal1 = personal1;
        line.personal2 = personal2;
        line.absent = absent;
        line.lateEarly = lateEarly;
        line.missing = missing;
        line.annual = annual;
        line.special = special;
        line.actualRest = actualRest;
        line.remaining = remaining;
        line.actualAttend = Math.max(0, line.shouldAttend - absent);
        line.supportAttend = 0;
        line.attendRate = line.shouldAttend <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(line.actualAttend)
                .divide(BigDecimal.valueOf(line.shouldAttend), 4, RoundingMode.HALF_UP);

        line.verbalPraise = rewardCount.getOrDefault(PayRewardKinds.VERBAL_PRAISE, 0);
        line.commendation = rewardCount.getOrDefault(PayRewardKinds.COMMENDATION, 0);
        line.merit = rewardCount.getOrDefault(PayRewardKinds.MERIT, 0);
        line.majorMerit = rewardCount.getOrDefault(PayRewardKinds.MAJOR_MERIT, 0);
        line.verbalCritic = rewardCount.getOrDefault(PayRewardKinds.VERBAL_CRITIC, 0);
        line.bulletinCritic = rewardCount.getOrDefault(PayRewardKinds.BULLETIN_CRITIC, 0);
        line.demerit = rewardCount.getOrDefault(PayRewardKinds.DEMERIT, 0);
        line.majorDemerit = rewardCount.getOrDefault(PayRewardKinds.MAJOR_DEMERIT, 0);

        line.salaryStandard = nz(emp.salaryStandard);
        line.amtPost = nz(emp.amtPost);
        line.amtFull = nz(emp.amtFull);
        line.amtSocial = nz(emp.amtSocial);
        line.amtHousing = nz(emp.amtHousing);
        line.amtTransport = nz(emp.amtTransport);
        line.amtNoncompete = nz(emp.amtNoncompete);
        line.amtHygiene = nz(emp.amtHygiene);
        line.amtFixedOt = nz(emp.amtFixedOt);
        line.baseSum = line.amtPost.add(line.amtFull).add(line.amtSocial).add(line.amtHousing)
                .add(line.amtTransport).add(line.amtNoncompete).add(line.amtHygiene).add(line.amtFixedOt)
                .setScale(2, RoundingMode.HALF_UP);
        if (line.baseSum.compareTo(BigDecimal.ZERO) == 0) {
            line.baseSum = line.salaryStandard;
        }
        line.attendWage = line.baseSum.multiply(line.attendRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal coeff = coefficient == null || coefficient.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : coefficient;
        line.perfWage = line.attendWage.multiply(coeff).setScale(2, RoundingMode.HALF_UP);
        line.extraPerf = BigDecimal.ZERO;
        line.commission = BigDecimal.ZERO;
        line.reissue = feeAdd.setScale(2, RoundingMode.HALF_UP);
        line.overprod = BigDecimal.ZERO;
        line.payable = line.perfWage.add(line.extraPerf).add(line.commission).add(line.reissue).add(line.overprod)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal[] lateMoney = PayrollBaseService.lateDeduct(lateEarly,
                lateRule == null ? 3 : lateRule.allowCount,
                lateRule == null ? BigDecimal.ZERO : lateRule.personal1,
                lateRule == null ? BigDecimal.ZERO : lateRule.personal2);
        line.rewardDeduct = rewardAmt.setScale(2, RoundingMode.HALF_UP);
        line.lateDeduct = lateMoney[0].add(lateMoney[1]).setScale(2, RoundingMode.HALF_UP);
        line.missingDeduct = BigDecimal.ZERO;
        line.compensation = BigDecimal.ZERO;
        line.meal = BigDecimal.ZERO;
        line.propertyFee = BigDecimal.ZERO;
        line.otherDeduct = feeDeduct.setScale(2, RoundingMode.HALF_UP);
        line.housingFund = social.housing().setScale(2, RoundingMode.HALF_UP);
        line.social = social.social().setScale(2, RoundingMode.HALF_UP);
        line.financeHold = BigDecimal.ZERO;
        line.tax = feeTax.setScale(2, RoundingMode.HALF_UP);
        // 通报奖惩金额可正可负；其余扣款为正数
        line.net = line.payable.add(line.rewardDeduct)
                .subtract(line.lateDeduct).subtract(line.missingDeduct).subtract(line.compensation)
                .subtract(line.meal).subtract(line.propertyFee).subtract(line.otherDeduct)
                .subtract(line.housingFund).subtract(line.social).subtract(line.financeHold).subtract(line.tax)
                .setScale(2, RoundingMode.HALF_UP);
        line.remark = buildRemark(att, remaining, actualRest, annual, social.ruleLabel(), emp.socialBase);
        return line;
    }

    private static String buildRemark(Att att, BigDecimal remaining, BigDecimal actualRest, BigDecimal annual,
                                      String socialRule, BigDecimal socialBase) {
        StringBuilder sb = new StringBuilder();
        if (att != null) {
            sb.append("剩余调休").append(remaining.stripTrailingZeros().toPlainString())
                    .append("天，本月实休").append(actualRest.stripTrailingZeros().toPlainString())
                    .append("天，年假").append(annual.stripTrailingZeros().toPlainString()).append("天");
        }
        if (StringUtils.hasText(socialRule) && socialBase != null) {
            if (!sb.isEmpty()) {
                sb.append("；");
            }
            sb.append("社保规则").append(socialRule)
                    .append("，缴费基数").append(socialBase.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    private void writeLine(Row row, Line line, CellStyle text, CellStyle money, CellStyle pct) {
        setInt(row, 0, line.seq, text);
        setText(row, 1, line.dept, text);
        setText(row, 2, line.hireDate, text);
        setText(row, 3, line.name, text);
        setText(row, 4, line.post, text);
        setInt(row, 5, line.shouldAttend, text);
        setInt(row, 6, line.actualAttend, text);
        setInt(row, 7, line.supportAttend, text);
        setPct(row, 8, line.attendRate, pct);
        setInt(row, 9, line.personal1, text);
        setInt(row, 10, line.personal2, text);
        setInt(row, 11, 0, text);
        setInt(row, 12, line.absent, text);
        setInt(row, 13, line.lateEarly, text);
        setInt(row, 14, line.missing, text);
        setDec(row, 15, line.annual, money);
        setDec(row, 16, line.special, money);
        setDec(row, 17, line.actualRest, money);
        setDec(row, 18, line.remaining, money);
        setInt(row, 19, line.verbalPraise, text);
        setInt(row, 20, line.commendation, text);
        setInt(row, 21, line.merit, text);
        setInt(row, 22, line.majorMerit, text);
        setInt(row, 23, line.verbalCritic, text);
        setInt(row, 24, line.bulletinCritic, text);
        setInt(row, 25, line.demerit, text);
        setInt(row, 26, line.majorDemerit, text);
        setDec(row, 27, line.salaryStandard, money);
        setDec(row, 28, line.amtPost, money);
        setDec(row, 29, line.amtFull, money);
        setDec(row, 30, line.amtSocial, money);
        setDec(row, 31, line.amtHousing, money);
        setDec(row, 32, line.amtTransport, money);
        setDec(row, 33, line.amtNoncompete, money);
        setDec(row, 34, line.amtHygiene, money);
        setDec(row, 35, line.amtFixedOt, money);
        setDec(row, 36, line.baseSum, money);
        setDec(row, 37, line.attendWage, money);
        setDec(row, 38, line.perfWage, money);
        setDec(row, 39, line.extraPerf, money);
        setDec(row, 40, line.commission, money);
        setDec(row, 41, line.reissue, money);
        setDec(row, 42, line.overprod, money);
        setDec(row, 43, line.payable, money);
        setDec(row, 44, line.rewardDeduct, money);
        setDec(row, 45, line.lateDeduct, money);
        setDec(row, 46, line.missingDeduct, money);
        setDec(row, 47, line.compensation, money);
        setDec(row, 48, line.meal, money);
        setDec(row, 49, line.propertyFee, money);
        setDec(row, 50, line.otherDeduct, money);
        setDec(row, 51, line.housingFund, money);
        setDec(row, 52, line.social, money);
        setDec(row, 53, line.financeHold, money);
        setDec(row, 54, line.tax, money);
        setDec(row, 55, line.net, money);
        setText(row, 56, line.remark, text);
    }

    private void writeSummary(Row row, BigDecimal[] sums, int should, int actual, int people,
                              CellStyle text, CellStyle money, CellStyle pct) {
        setText(row, 0, "汇总", text);
        setInt(row, 5, should, text);
        setInt(row, 6, actual, text);
        setInt(row, 7, 0, text);
        BigDecimal rate = should <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(actual).divide(BigDecimal.valueOf(should), 4, RoundingMode.HALF_UP);
        setPct(row, 8, rate, pct);
        for (int c = 9; c <= 26; c++) {
            setDec(row, c, sums[c], money);
        }
        for (int c = 27; c <= 55; c++) {
            setDec(row, c, sums[c], money);
        }
        setText(row, 56, "共 " + people + " 人", text);
    }

    private void accumulate(BigDecimal[] sums, Line line) {
        add(sums, 9, line.personal1);
        add(sums, 10, line.personal2);
        add(sums, 12, line.absent);
        add(sums, 13, line.lateEarly);
        add(sums, 14, line.missing);
        add(sums, 15, line.annual);
        add(sums, 16, line.special);
        add(sums, 17, line.actualRest);
        add(sums, 18, line.remaining);
        add(sums, 19, line.verbalPraise);
        add(sums, 20, line.commendation);
        add(sums, 21, line.merit);
        add(sums, 22, line.majorMerit);
        add(sums, 23, line.verbalCritic);
        add(sums, 24, line.bulletinCritic);
        add(sums, 25, line.demerit);
        add(sums, 26, line.majorDemerit);
        add(sums, 27, line.salaryStandard);
        add(sums, 28, line.amtPost);
        add(sums, 29, line.amtFull);
        add(sums, 30, line.amtSocial);
        add(sums, 31, line.amtHousing);
        add(sums, 32, line.amtTransport);
        add(sums, 33, line.amtNoncompete);
        add(sums, 34, line.amtHygiene);
        add(sums, 35, line.amtFixedOt);
        add(sums, 36, line.baseSum);
        add(sums, 37, line.attendWage);
        add(sums, 38, line.perfWage);
        add(sums, 39, line.extraPerf);
        add(sums, 40, line.commission);
        add(sums, 41, line.reissue);
        add(sums, 42, line.overprod);
        add(sums, 43, line.payable);
        add(sums, 44, line.rewardDeduct);
        add(sums, 45, line.lateDeduct);
        add(sums, 46, line.missingDeduct);
        add(sums, 47, line.compensation);
        add(sums, 48, line.meal);
        add(sums, 49, line.propertyFee);
        add(sums, 50, line.otherDeduct);
        add(sums, 51, line.housingFund);
        add(sums, 52, line.social);
        add(sums, 53, line.financeHold);
        add(sums, 54, line.tax);
        add(sums, 55, line.net);
    }

    private static void add(BigDecimal[] sums, int col, Number value) {
        sums[col] = sums[col].add(value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()));
    }

    private List<Emp> loadEmployees() {
        return jdbc.query("""
                SELECT e.id, e.employee_name, e.hire_date, e.grade_id, e.salary_standard,
                       e.amt_post, e.amt_full_attendance, e.amt_social_subsidy, e.amt_housing_subsidy,
                       e.amt_transport, e.amt_noncompete, e.amt_hygiene, e.amt_fixed_overtime,
                       e.hukou_type, e.social_base, d.dept_name, p.post_name
                FROM pay_employee e
                LEFT JOIN pay_dept d ON d.id = e.dept_id AND d.is_active = 1
                LEFT JOIN pay_post p ON p.id = e.post_id AND p.is_active = 1
                WHERE e.is_active = 1 AND e.job_status = 1
                ORDER BY d.sort_order, d.id, e.id
                """, (rs, row) -> {
            Emp emp = new Emp();
            emp.id = rs.getLong("id");
            emp.name = rs.getString("employee_name");
            emp.dept = blank(rs.getString("dept_name"));
            emp.post = blank(rs.getString("post_name"));
            emp.gradeId = rs.getLong("grade_id");
            emp.hukouType = blank(rs.getString("hukou_type"));
            emp.socialBase = rs.getBigDecimal("social_base");
            java.sql.Date hire = rs.getDate("hire_date");
            emp.hireDate = hire == null ? null : hire.toLocalDate();
            emp.salaryStandard = rs.getBigDecimal("salary_standard");
            emp.amtPost = rs.getBigDecimal("amt_post");
            emp.amtFull = rs.getBigDecimal("amt_full_attendance");
            emp.amtSocial = rs.getBigDecimal("amt_social_subsidy");
            emp.amtHousing = rs.getBigDecimal("amt_housing_subsidy");
            emp.amtTransport = rs.getBigDecimal("amt_transport");
            emp.amtNoncompete = rs.getBigDecimal("amt_noncompete");
            emp.amtHygiene = rs.getBigDecimal("amt_hygiene");
            emp.amtFixedOt = rs.getBigDecimal("amt_fixed_overtime");
            return emp;
        });
    }

    private Map<Long, Att> loadAttendance(String period) {
        Map<Long, Att> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT employee_id, late_early_count, personal1_count, personal2_count, absent_count, missing_card_count,
                       annual_leave_days, special_leave_days, actual_rest_days, remaining_comp_days
                FROM pay_attendance_summary WHERE period = ? AND employee_id > 0
                """, period);
        for (Map<String, Object> row : rows) {
            Att att = new Att();
            att.lateEarly = ((Number) row.get("late_early_count")).intValue();
            att.personal1 = ((Number) row.get("personal1_count")).intValue();
            att.personal2 = ((Number) row.get("personal2_count")).intValue();
            att.absent = ((Number) row.get("absent_count")).intValue();
            att.missing = ((Number) row.get("missing_card_count")).intValue();
            att.annual = nz((BigDecimal) row.get("annual_leave_days"));
            att.special = nz((BigDecimal) row.get("special_leave_days"));
            att.actualRest = nz((BigDecimal) row.get("actual_rest_days"));
            att.remaining = nz((BigDecimal) row.get("remaining_comp_days"));
            map.put(((Number) row.get("employee_id")).longValue(), att);
        }
        return map;
    }

    private Map<Long, Map<String, Integer>> loadRewardCounts(String period) {
        Map<Long, Map<String, Integer>> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT employee_id, reward_kind, SUM(times) AS cnt
                FROM pay_reward
                WHERE is_active = 1 AND period = ?
                GROUP BY employee_id, reward_kind
                """, period);
        for (Map<String, Object> row : rows) {
            Long empId = ((Number) row.get("employee_id")).longValue();
            map.computeIfAbsent(empId, id -> new HashMap<>())
                    .put(String.valueOf(row.get("reward_kind")), ((Number) row.get("cnt")).intValue());
        }
        return map;
    }

    private Map<Long, BigDecimal> loadRewardAmount(String period) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT employee_id, SUM(amount) AS amt
                FROM pay_reward
                WHERE is_active = 1 AND period = ?
                GROUP BY employee_id
                """, period);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("employee_id")).longValue(), nz((BigDecimal) row.get("amt")));
        }
        return map;
    }

    private Map<Long, BigDecimal> loadPerformance(String period) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT employee_id, coefficient FROM pay_performance
                WHERE is_active = 1 AND period = ?
                """, period);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("employee_id")).longValue(), nz((BigDecimal) row.get("coefficient")));
        }
        return map;
    }

    /**
     * @param excludeCategory 非空时排除该类目（扣费里把「个税」拆到个税列）
     */
    private Map<Long, BigDecimal> loadFees(String period, String feeKind, String excludeCategory) {
        Map<Long, BigDecimal> map = new HashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT f.employee_id, SUM(f.amount) AS amt
                FROM pay_employee_fee f
                JOIN pay_fee_category c ON c.id = f.category_id AND c.is_active = 1
                WHERE f.is_active = 1 AND f.fee_month = ? AND c.fee_kind = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(period);
        args.add(feeKind);
        if (StringUtils.hasText(excludeCategory)) {
            sql.append(" AND c.category_name <> ?");
            args.add(excludeCategory);
        }
        sql.append(" GROUP BY f.employee_id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("employee_id")).longValue(), nz((BigDecimal) row.get("amt")));
        }
        return map;
    }

    private Map<Long, BigDecimal> loadFeesByCategory(String period, String categoryName) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.employee_id, SUM(f.amount) AS amt
                FROM pay_employee_fee f
                JOIN pay_fee_category c ON c.id = f.category_id AND c.is_active = 1
                WHERE f.is_active = 1 AND f.fee_month = ? AND c.category_name = ?
                GROUP BY f.employee_id
                """, period, categoryName);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("employee_id")).longValue(), nz((BigDecimal) row.get("amt")));
        }
        return map;
    }

    private Map<Long, LateRule> loadLateRules() {
        Map<Long, LateRule> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT grade_id, allow_count, personal1_amount, personal2_amount
                FROM pay_late_rule WHERE is_active = 1
                """);
        for (Map<String, Object> row : rows) {
            map.put(((Number) row.get("grade_id")).longValue(),
                    new LateRule(((Number) row.get("allow_count")).intValue(),
                            nz((BigDecimal) row.get("personal1_amount")),
                            nz((BigDecimal) row.get("personal2_amount"))));
        }
        return map;
    }

    private int loadShouldAttend(int year, int month) {
        List<Integer> rows = jdbc.query("""
                SELECT dual_work FROM pay_work_calendar WHERE cal_year = ? AND cal_month = ?
                """, (rs, row) -> rs.getInt(1), year, month);
        if (!rows.isEmpty() && rows.get(0) != null && rows.get(0) > 0) {
            return rows.get(0);
        }
        return LocalDate.of(year, month, 1).lengthOfMonth();
    }

    private Job requireJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException("导出任务不存在或已过期");
        }
        return job;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Job>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Job> entry = it.next();
            Job job = entry.getValue();
            if (job.expireAt > 0 && job.expireAt < now) {
                if (job.file != null) {
                    try {
                        Files.deleteIfExists(job.file);
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
                it.remove();
            }
        }
    }

    private static PayWageExportJobVO toVo(Job job) {
        PayWageExportJobVO vo = new PayWageExportJobVO();
        vo.setJobId(job.jobId);
        vo.setPeriod(job.period);
        vo.setStatus(job.status);
        vo.setPercent(job.percent);
        vo.setMessage(job.message);
        vo.setFileName(job.fileName);
        return vo;
    }

    private static void clearRow(Row row) {
        for (int c = row.getLastCellNum() - 1; c >= 0; c--) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                row.removeCell(cell);
            }
        }
    }

    private static CellStyle dataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private static CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = dataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    private static CellStyle pctStyle(Workbook workbook) {
        CellStyle style = dataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        return style;
    }

    private static void setText(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void setInt(Row row, int col, int value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void setDec(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static void setPct(Row row, int col, BigDecimal rate, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(rate).doubleValue());
        cell.setCellStyle(style);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        for (Job job : jobs.values()) {
            if (job.file != null) {
                try {
                    Files.deleteIfExists(job.file);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        jobs.clear();
    }

    private static final class Job {
        private final String jobId;
        private final String period;
        private volatile String status;
        private volatile int percent;
        private volatile String message;
        private volatile String fileName;
        private volatile Path file;
        private volatile long expireAt;

        private Job(String jobId, String period) {
            this.jobId = jobId;
            this.period = period;
        }
    }

    private static final class Emp {
        private long id;
        private String name;
        private String dept;
        private String post;
        private long gradeId;
        private String hukouType;
        private BigDecimal socialBase;
        private LocalDate hireDate;
        private BigDecimal salaryStandard;
        private BigDecimal amtPost;
        private BigDecimal amtFull;
        private BigDecimal amtSocial;
        private BigDecimal amtHousing;
        private BigDecimal amtTransport;
        private BigDecimal amtNoncompete;
        private BigDecimal amtHygiene;
        private BigDecimal amtFixedOt;
    }

    private static final class Att {
        private int lateEarly;
        private int personal1;
        private int personal2;
        private int absent;
        private int missing;
        private BigDecimal annual = BigDecimal.ZERO;
        private BigDecimal special = BigDecimal.ZERO;
        private BigDecimal actualRest = BigDecimal.ZERO;
        private BigDecimal remaining = BigDecimal.ZERO;
    }

    private record LateRule(int allowCount, BigDecimal personal1, BigDecimal personal2) {
    }

    private static final class Line {
        private int seq;
        private String dept;
        private String hireDate;
        private String name;
        private String post;
        private int shouldAttend;
        private int actualAttend;
        private int supportAttend;
        private BigDecimal attendRate;
        private int personal1;
        private int personal2;
        private int absent;
        private int lateEarly;
        private int missing;
        private BigDecimal annual;
        private BigDecimal special;
        private BigDecimal actualRest;
        private BigDecimal remaining;
        private int verbalPraise;
        private int commendation;
        private int merit;
        private int majorMerit;
        private int verbalCritic;
        private int bulletinCritic;
        private int demerit;
        private int majorDemerit;
        private BigDecimal salaryStandard;
        private BigDecimal amtPost;
        private BigDecimal amtFull;
        private BigDecimal amtSocial;
        private BigDecimal amtHousing;
        private BigDecimal amtTransport;
        private BigDecimal amtNoncompete;
        private BigDecimal amtHygiene;
        private BigDecimal amtFixedOt;
        private BigDecimal baseSum;
        private BigDecimal attendWage;
        private BigDecimal perfWage;
        private BigDecimal extraPerf;
        private BigDecimal commission;
        private BigDecimal reissue;
        private BigDecimal overprod;
        private BigDecimal payable;
        private BigDecimal rewardDeduct;
        private BigDecimal lateDeduct;
        private BigDecimal missingDeduct;
        private BigDecimal compensation;
        private BigDecimal meal;
        private BigDecimal propertyFee;
        private BigDecimal otherDeduct;
        private BigDecimal housingFund;
        private BigDecimal social;
        private BigDecimal financeHold;
        private BigDecimal tax;
        private BigDecimal net;
        private String remark;
    }
}
