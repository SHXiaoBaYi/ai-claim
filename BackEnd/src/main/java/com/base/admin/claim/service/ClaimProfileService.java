package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.claim.domain.ClaimCase;
import com.base.admin.claim.domain.ClaimCustomer;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.domain.ClaimSale;
import com.base.admin.claim.dto.ClaimPageQueryDTO;
import com.base.admin.claim.mapper.ClaimCaseMapper;
import com.base.admin.claim.mapper.ClaimCustomerMapper;
import com.base.admin.claim.mapper.ClaimHistoryCaseMapper;
import com.base.admin.claim.mapper.ClaimSaleMapper;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimProfileService {

    public static final String SOURCE_ARCHIVE = "ARCHIVE";
    public static final String SOURCE_HISTORY = "HISTORY";

    private final ClaimCustomerMapper customerMapper;
    private final ClaimSaleMapper saleMapper;
    private final ClaimCaseMapper caseMapper;
    private final ClaimHistoryCaseMapper historyMapper;

    /** 手机号在历史案例、其他档案或已积累的客户/销售里出现过，就按老客处理。 */
    public void recognize(ClaimCase claim) {
        String phone = ClaimTexts.phoneOf(claim.getCustomerPhone());
        if (phone.isBlank()) {
            return;
        }
        long history = countHistory(phone);
        long archives = countArchives(phone, claim.getId());
        long sales = countSales(phone, claim.getId());
        if (history + archives + sales == 0) {
            return;
        }
        claim.setCustomerType("老客");
        String note = summary(phone, history, archives, sales);
        String consume = ClaimTexts.empty(claim.getConsumeSummary());
        if (consume.isBlank()) {
            claim.setConsumeSummary(note);
        } else if (!consume.contains("已有记录")) {
            claim.setConsumeSummary(ClaimTexts.clip(consume + "；" + note, 500));
        }
    }

    @Transactional
    public void rememberArchive(ClaimCase claim) {
        String phone = ClaimTexts.phoneOf(claim.getCustomerPhone());
        if (phone.isBlank() || claim.getId() == null) {
            return;
        }
        upsertSale(phone, claim.getCustomerName(), SOURCE_ARCHIVE, claim.getId(), claim.getStoreName(),
                claim.getProductCategory(), claim.getProductName(), claim.getProductSpec(),
                ClaimTexts.empty(claim.getPurchaseDate()).isBlank() ? "" : claim.getPurchaseDate(),
                claim.getOrderNo(), claim.getProductName());
        refreshCustomer(phone, claim.getCustomerName(), claim.getCustomerAddress(), claim.getMemberNo(),
                claim.getStoreName(), claim.getProductName(), claim.getPurchaseDate());
    }

    @Transactional
    public void rememberHistory(ClaimHistoryCase row) {
        String phone = ClaimTexts.phoneOf(row.getCustomerPhone());
        if (phone.isBlank() || row.getId() == null) {
            return;
        }
        String date = ClaimTexts.empty(row.getPurchaseDate()).isBlank() ? row.getComplaintDate() : row.getPurchaseDate();
        upsertSale(phone, row.getCustomerName(), SOURCE_HISTORY, row.getId(), row.getStoreName(),
                row.getProductCategory(), row.getProductName(), "", date, "", row.getSymptomBrief());
        refreshCustomer(phone, row.getCustomerName(), row.getCustomerAddress(), "",
                row.getStoreName(), row.getProductName(), date);
    }

    @Transactional
    public void forgetArchive(Long caseId, String rawPhone) {
        if (caseId != null) {
            ClaimSale sale = saleMapper.selectOne(new LambdaQueryWrapper<ClaimSale>()
                    .eq(ClaimSale::getSource, SOURCE_ARCHIVE)
                    .eq(ClaimSale::getSourceId, caseId)
                    .last("limit 1"));
            if (sale != null) {
                saleMapper.deleteById(sale.getId());
            }
        }
        String phone = ClaimTexts.phoneOf(rawPhone);
        if (!phone.isBlank()) {
            refreshCustomer(phone, "", "", "", "", "", "");
        }
    }

    public void backfillIfEmpty() {
        Long count = customerMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        List<ClaimHistoryCase> history = historyMapper.selectList(new LambdaQueryWrapper<ClaimHistoryCase>()
                .orderByAsc(ClaimHistoryCase::getId));
        for (ClaimHistoryCase row : history) {
            rememberHistory(row);
        }
        List<ClaimCase> archives = caseMapper.selectList(new LambdaQueryWrapper<ClaimCase>()
                .orderByAsc(ClaimCase::getId));
        for (ClaimCase row : archives) {
            rememberArchive(row);
        }
        log.info("已从历史案例和理赔档案积累客户 {} 人、销售 {} 笔",
                customerMapper.selectCount(new LambdaQueryWrapper<>()),
                saleMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    public PageResult<ClaimCustomer> pageCustomers(ClaimPageQueryDTO query) {
        String phone = ClaimTexts.phoneOf(query.getCustomerPhone());
        LambdaQueryWrapper<ClaimCustomer> wrapper = new LambdaQueryWrapper<ClaimCustomer>()
                .eq(!phone.isBlank(), ClaimCustomer::getPhone, phone)
                .like(!ClaimTexts.blank(query.getCustomerName()), ClaimCustomer::getCustomerName, query.getCustomerName())
                .and(!ClaimTexts.blank(query.getKeyword()), w -> w
                        .like(ClaimCustomer::getPhone, query.getKeyword())
                        .or().like(ClaimCustomer::getCustomerName, query.getKeyword())
                        .or().like(ClaimCustomer::getLastProduct, query.getKeyword()))
                .orderByDesc(ClaimCustomer::getId);
        Page<ClaimCustomer> page = customerMapper.selectPage(new Page<>(pageNum(query), pageSize(query)), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public PageResult<ClaimSale> pageSales(ClaimPageQueryDTO query) {
        String phone = ClaimTexts.phoneOf(query.getCustomerPhone());
        LambdaQueryWrapper<ClaimSale> wrapper = new LambdaQueryWrapper<ClaimSale>()
                .eq(!phone.isBlank(), ClaimSale::getPhone, phone)
                .like(!ClaimTexts.blank(query.getCustomerName()), ClaimSale::getCustomerName, query.getCustomerName())
                .like(!ClaimTexts.blank(query.getProductName()), ClaimSale::getProductName, query.getProductName())
                .and(!ClaimTexts.blank(query.getKeyword()), w -> w
                        .like(ClaimSale::getPhone, query.getKeyword())
                        .or().like(ClaimSale::getCustomerName, query.getKeyword())
                        .or().like(ClaimSale::getProductName, query.getKeyword())
                        .or().like(ClaimSale::getStoreName, query.getKeyword()))
                .orderByDesc(ClaimSale::getId);
        Page<ClaimSale> page = saleMapper.selectPage(new Page<>(pageNum(query), pageSize(query)), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    private void upsertSale(String phone, String name, String source, Long sourceId, String store, String category,
                            String product, String spec, String date, String orderNo, String summary) {
        ClaimSale found = saleMapper.selectOne(new LambdaQueryWrapper<ClaimSale>()
                .eq(ClaimSale::getSource, source)
                .eq(ClaimSale::getSourceId, sourceId)
                .last("limit 1"));
        ClaimSale row = found == null ? new ClaimSale() : found;
        row.setPhone(phone);
        row.setCustomerName(ClaimTexts.clip(name, 64));
        row.setSource(source);
        row.setSourceId(sourceId);
        row.setStoreName(ClaimTexts.clip(store, 128));
        row.setProductCategory(ClaimTexts.clip(category, 64));
        row.setProductName(ClaimTexts.clip(product, 255));
        row.setProductSpec(ClaimTexts.clip(spec, 64));
        row.setPurchaseDate(ClaimTexts.clip(date, 32));
        row.setOrderNo(ClaimTexts.clip(orderNo, 64));
        row.setSummary(ClaimTexts.clip(summary, 255));
        if (found == null) {
            saleMapper.insert(row);
        } else {
            saleMapper.updateById(row);
        }
    }

    private void refreshCustomer(String phone, String name, String address, String memberNo, String store, String product, String seen) {
        long history = countHistory(phone);
        long archives = countArchives(phone, null);
        long sales = saleMapper.selectCount(new LambdaQueryWrapper<ClaimSale>().eq(ClaimSale::getPhone, phone));
        ClaimCustomer found = customerMapper.selectOne(new LambdaQueryWrapper<ClaimCustomer>()
                .eq(ClaimCustomer::getPhone, phone)
                .last("limit 1"));
        if (history + archives + sales == 0) {
            if (found != null) {
                customerMapper.deleteById(found.getId());
            }
            return;
        }
        ClaimCustomer row = found == null ? new ClaimCustomer() : found;
        row.setPhone(phone);
        if (!ClaimTexts.blank(name)) {
            row.setCustomerName(ClaimTexts.clip(name, 64));
        }
        if (!ClaimTexts.blank(address)) {
            row.setCustomerAddress(ClaimTexts.clip(address, 255));
        }
        if (!ClaimTexts.blank(memberNo)) {
            row.setMemberNo(ClaimTexts.clip(memberNo, 64));
        }
        row.setCustomerType(history + archives > 1 || history > 0 ? "老客" : "新客");
        if (archives > 1 || history > 0) {
            row.setCustomerType("老客");
        }
        row.setHistoryCount((int) history);
        row.setArchiveCount((int) archives);
        row.setSaleCount((int) sales);
        if (!ClaimTexts.blank(store)) {
            row.setLastStore(ClaimTexts.clip(store, 128));
        }
        if (!ClaimTexts.blank(product)) {
            row.setLastProduct(ClaimTexts.clip(product, 255));
        }
        if (!ClaimTexts.blank(seen)) {
            row.setLastSeen(ClaimTexts.clip(seen, 32));
        }
        if (found == null) {
            customerMapper.insert(row);
        } else {
            customerMapper.updateById(row);
        }
    }

    private String summary(String phone, long history, long archives, long sales) {
        String recent = "";
        ClaimSale sale = saleMapper.selectOne(new LambdaQueryWrapper<ClaimSale>()
                .eq(ClaimSale::getPhone, phone)
                .orderByDesc(ClaimSale::getId)
                .last("limit 1"));
        if (sale != null) {
            recent = ClaimTexts.empty(sale.getPurchaseDate()) + " " + ClaimTexts.empty(sale.getProductName())
                    + " " + ClaimTexts.empty(sale.getStoreName());
        }
        return ClaimTexts.clip("已有记录：历史投诉 " + history + " 次，理赔档案 " + archives + " 次，销售 " + sales
                + " 笔" + (recent.isBlank() ? "" : "。最近 " + recent.trim()), 500);
    }

    private long countHistory(String phone) {
        Long count = historyMapper.selectCount(new LambdaQueryWrapper<ClaimHistoryCase>()
                .eq(ClaimHistoryCase::getCustomerPhone, phone));
        return count == null ? 0 : count;
    }

    private long countArchives(String phone, Long excludeId) {
        Long count = caseMapper.selectCount(new LambdaQueryWrapper<ClaimCase>()
                .eq(ClaimCase::getCustomerPhone, phone)
                .ne(excludeId != null, ClaimCase::getId, excludeId));
        return count == null ? 0 : count;
    }

    private long countSales(String phone, Long excludeCaseId) {
        Long count = saleMapper.selectCount(new LambdaQueryWrapper<ClaimSale>()
                .eq(ClaimSale::getPhone, phone)
                .and(excludeCaseId != null, w -> w
                        .ne(ClaimSale::getSource, SOURCE_ARCHIVE)
                        .or()
                        .ne(ClaimSale::getSourceId, excludeCaseId)));
        return count == null ? 0 : count;
    }

    private long pageNum(ClaimPageQueryDTO query) {
        return query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
    }

    private long pageSize(ClaimPageQueryDTO query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }
}
