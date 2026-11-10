package com.base.admin.service;

import com.base.admin.domain.dto.PayWorkCalendarMonthDTO;
import com.base.admin.domain.dto.PayWorkCalendarSaveDTO;
import com.base.admin.domain.vo.PayWorkCalendarVO;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PayrollCalendarService {

    private final JdbcTemplate jdbc;

    public List<PayWorkCalendarVO> list(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("年份不正确");
        }
        return jdbc.query("""
                SELECT cal_year, cal_month, calendar_days, holiday_name, holiday_days,
                       dual_rest, dual_work, dual_pay,
                       single_rest, single_work, single_pay,
                       alt_rest, alt_work, alt_pay
                FROM pay_work_calendar
                WHERE cal_year = ?
                ORDER BY cal_month
                """, (rs, i) -> {
            PayWorkCalendarVO vo = new PayWorkCalendarVO();
            vo.setYear(rs.getInt("cal_year"));
            vo.setMonth(rs.getInt("cal_month"));
            vo.setCalendarDays(rs.getInt("calendar_days"));
            vo.setHolidayName(rs.getString("holiday_name"));
            vo.setHolidayDays(rs.getInt("holiday_days"));
            vo.setDualRest(rs.getInt("dual_rest"));
            vo.setDualWork(rs.getInt("dual_work"));
            vo.setDualPay(rs.getInt("dual_pay"));
            vo.setSingleRest(rs.getInt("single_rest"));
            vo.setSingleWork(rs.getInt("single_work"));
            vo.setSinglePay(rs.getInt("single_pay"));
            vo.setAltRest(rs.getInt("alt_rest"));
            vo.setAltWork(rs.getInt("alt_work"));
            vo.setAltPay(rs.getInt("alt_pay"));
            return vo;
        }, year);
    }

    @Transactional
    public void save(PayWorkCalendarSaveDTO dto) {
        Set<Integer> months = new HashSet<>();
        for (PayWorkCalendarMonthDTO month : dto.getMonths()) {
            if (!months.add(month.getMonth())) {
                throw new BusinessException("月份重复");
            }
            String name = month.getHolidayName() == null ? "" : month.getHolidayName().trim();
            int updated = jdbc.update("""
                    UPDATE pay_work_calendar
                    SET holiday_name = ?, holiday_days = ?,
                        dual_rest = ?, dual_work = ?, dual_pay = ?,
                        single_rest = ?, single_work = ?, single_pay = ?,
                        alt_rest = ?, alt_work = ?, alt_pay = ?
                    WHERE cal_year = ? AND cal_month = ?
                    """, name, month.getHolidayDays(),
                    month.getDualRest(), month.getDualWork(), month.getDualPay(),
                    month.getSingleRest(), month.getSingleWork(), month.getSinglePay(),
                    month.getAltRest(), month.getAltWork(), month.getAltPay(),
                    dto.getYear(), month.getMonth());
            if (updated == 0) {
                throw new BusinessException(dto.getYear() + "年" + month.getMonth() + "月还没有初始数据");
            }
        }
    }
}
