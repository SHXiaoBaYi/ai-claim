package com.base.admin.claim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.claim.ClaimPaths;
import com.base.admin.claim.domain.ClaimHistoryCase;
import com.base.admin.claim.dto.ClaimHistoryUpdateDTO;
import com.base.admin.claim.dto.ClaimImportResultVO;
import com.base.admin.claim.dto.ClaimPageQueryDTO;
import com.base.admin.claim.mapper.ClaimHistoryCaseMapper;
import com.base.admin.claim.support.ClaimHistoryExcel;
import com.base.admin.claim.support.ClaimHistoryExcel.SheetTable;
import com.base.admin.claim.support.ClaimProductGroup;
import com.base.admin.claim.support.ClaimSymptomCategory;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.common.PageResult;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimHistoryService {

    private final ClaimHistoryCaseMapper historyMapper;
    private final ClaimPaths paths;

    public PageResult<ClaimHistoryCase> page(ClaimPageQueryDTO query) {
        LambdaQueryWrapper<ClaimHistoryCase> wrapper = new LambdaQueryWrapper<ClaimHistoryCase>()
                .eq(!ClaimTexts.blank(query.getSourceYear()), ClaimHistoryCase::getSourceYear, query.getSourceYear())
                .and(!ClaimTexts.blank(query.getKeyword()), w -> w
                        .like(ClaimHistoryCase::getCustomerName, query.getKeyword())
                        .or().like(ClaimHistoryCase::getCustomerPhone, query.getKeyword())
                        .or().like(ClaimHistoryCase::getProductName, query.getKeyword())
                        .or().like(ClaimHistoryCase::getSymptomBrief, query.getKeyword())
                        .or().like(ClaimHistoryCase::getSymptomCategory, query.getKeyword())
                        .or().like(ClaimHistoryCase::getProductGroup, query.getKeyword())
                        .or().like(ClaimHistoryCase::getComplaintContent, query.getKeyword()))
                .orderByDesc(ClaimHistoryCase::getComplaintDate)
                .orderByDesc(ClaimHistoryCase::getId);
        Page<ClaimHistoryCase> page = historyMapper.selectPage(
                new Page<>(pageNum(query), pageSize(query)), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public ClaimHistoryCase get(Long id) {
        ClaimHistoryCase row = historyMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("历史案例不存在");
        }
        return row;
    }

    public void update(ClaimHistoryUpdateDTO dto) {
        ClaimHistoryCase row = get(dto.getId());
        boolean textChanged = changed(row.getSymptomBrief(), dto.getSymptomBrief())
                || changed(row.getProductName(), dto.getProductName())
                || changed(row.getProductCategory(), dto.getProductCategory())
                || changed(row.getComplaintType(), dto.getComplaintType())
                || changed(row.getComplaintContent(), dto.getComplaintContent());
        String previousCategory = row.getSymptomCategory();
        String previousGroup = row.getProductGroup();
        row.setComplaintDate(ClaimTexts.empty(dto.getComplaintDate()));
        row.setPurchaseDate(ClaimTexts.empty(dto.getPurchaseDate()));
        row.setComplaintType(ClaimTexts.empty(dto.getComplaintType()));
        row.setStoreName(ClaimTexts.empty(dto.getStoreName()));
        row.setCustomerName(ClaimTexts.empty(dto.getCustomerName()));
        row.setCustomerPhone(ClaimTexts.empty(dto.getCustomerPhone()));
        row.setCustomerAddress(ClaimTexts.empty(dto.getCustomerAddress()));
        row.setComplaintContent(ClaimTexts.empty(dto.getComplaintContent()));
        row.setProductCategory(ClaimTexts.empty(dto.getProductCategory()));
        row.setProductGroup(ClaimSymptomCategory.choose(dto.getProductGroup(), previousGroup, textChanged,
                ClaimProductGroup.of(dto.getProductName(), dto.getProductCategory(), dto.getSymptomBrief())));
        row.setProductName(ClaimTexts.empty(dto.getProductName()));
        row.setSymptomBrief(ClaimTexts.empty(dto.getSymptomBrief()));
        row.setSymptomCategory(ClaimSymptomCategory.choose(dto.getSymptomCategory(), previousCategory, textChanged,
                ClaimSymptomCategory.classify(dto.getSymptomBrief(), dto.getProductName(), dto.getProductCategory(),
                        dto.getComplaintType(), dto.getComplaintContent())));
        row.setClaimOpinion(ClaimTexts.empty(dto.getClaimOpinion()));
        row.setClaimProduct(ClaimTexts.empty(dto.getClaimProduct()));
        row.setJudgment(ClaimTexts.empty(dto.getJudgment()));
        row.setThirdPartyAmount(ClaimTexts.empty(dto.getThirdPartyAmount()));
        row.setFundAmount(ClaimTexts.empty(dto.getFundAmount()));
        row.setDutyAmount(ClaimTexts.empty(dto.getDutyAmount()));
        row.setFreight(ClaimTexts.empty(dto.getFreight()));
        row.setResultText(ClaimTexts.empty(dto.getResultText()));
        row.setAdminResult(ClaimTexts.empty(dto.getAdminResult()));
        row.setDutyDept(ClaimTexts.empty(dto.getDutyDept()));
        row.setHandlerName(ClaimTexts.empty(dto.getHandlerName()));
        row.setManualEdited(1);
        historyMapper.updateById(row);
    }

    public void importIfEmpty() {
        Long count = historyMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        ClaimImportResultVO result = importFromDocs();
        log.info("已导入历史案例：{}", result.getMessage());
    }

    @Transactional
    public ClaimImportResultVO importFromDocs() {
        Path dir = paths.historyDir();
        if (!Files.isDirectory(dir)) {
            throw new BusinessException("找不到历史案例目录：" + dir);
        }
        ClaimImportResultVO result = new ClaimImportResultVO();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> excels = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".xlsx") && name.contains("客诉") && !name.startsWith(".~");
            }).sorted().toList();
            if (excels.isEmpty()) {
                throw new BusinessException("目录里没有客诉 Excel：" + dir);
            }
            for (Path file : excels) {
                importFile(file, result);
            }
        } catch (IOException ex) {
            throw new BusinessException("读取历史案例失败");
        }
        result.setMessage("新增 " + result.getInserted() + " 条，更新 " + result.getUpdated() + " 条，保留手工修改 " + result.getSkipped() + " 条");
        return result;
    }

    private void importFile(Path file, ClaimImportResultVO result) throws IOException {
        String fileName = file.getFileName().toString();
        String year = fileName.contains("2024") ? "2024" : fileName.contains("2025") ? "2025" : fileName.contains("2026") ? "2026" : "";
        List<SheetTable> tables = ClaimHistoryExcel.read(file);
        if ("2026".equals(year)) {
            tables = tables.stream().filter(table -> "全年统计".equals(table.name())).toList();
        } else if (!tables.isEmpty()) {
            tables = List.of(tables.get(0));
        }
        for (SheetTable table : tables) {
            for (Map<String, String> row : table.rows()) {
                upsert(fileName, year, row, result);
            }
        }
    }

    private void upsert(String fileName, String year, Map<String, String> row, ClaimImportResultVO result) {
        String content = ClaimHistoryExcel.text(row, "投诉内容");
        String name = ClaimHistoryExcel.text(row, "客户姓名", "顾客");
        String product = ClaimHistoryExcel.text(row, "产品名称");
        String brief = ClaimHistoryExcel.text(row, "投诉简述");
        if (ClaimTexts.blank(content) && ClaimTexts.blank(name) && ClaimTexts.blank(product) && ClaimTexts.blank(brief)) {
            return;
        }
        String phone = ClaimTexts.phoneOf(name + " " + content);
        String rawDate = ClaimHistoryExcel.text(row, "投诉日期");
        String rawPurchase = ClaimHistoryExcel.text(row, "购买日期");
        String date = alignYear(rawDate, year);
        String purchase = alignYear(rawPurchase, year);
        ClaimHistoryCase found = historyMapper.selectOne(new LambdaQueryWrapper<ClaimHistoryCase>()
                .eq(ClaimHistoryCase::getDedupeKey, dedupe(year, date, phone, name, product, brief))
                .last("limit 1"));
        if (found == null && !date.equals(rawDate)) {
            found = historyMapper.selectOne(new LambdaQueryWrapper<ClaimHistoryCase>()
                    .eq(ClaimHistoryCase::getDedupeKey, dedupe(year, rawDate, phone, name, product, brief))
                    .last("limit 1"));
        }
        if (found != null && found.getManualEdited() != null && found.getManualEdited() == 1) {
            result.setSkipped(result.getSkipped() + 1);
            return;
        }
        ClaimHistoryCase target = found == null ? new ClaimHistoryCase() : found;
        target.setDedupeKey(dedupe(year, date, phone, name, product, brief));
        target.setSourceYear(year);
        target.setSourceFile(fileName);
        target.setComplaintDate(date);
        target.setPurchaseDate(purchase);
        target.setComplaintType(ClaimHistoryExcel.text(row, "投诉类型"));
        target.setStoreName(ClaimHistoryExcel.text(row, "购买门店", "店面"));
        target.setRevisit(ClaimHistoryExcel.text(row, "是否回访"));
        target.setCustomerName(ClaimTexts.clip(name, 64));
        target.setCustomerPhone(phone);
        target.setCustomerAddress(ClaimTexts.addressOf(content));
        target.setComplaintContent(content);
        target.setProductCategory(ClaimTexts.clip(ClaimHistoryExcel.text(row, "产品分类"), 64));
        target.setProductGroup(ClaimProductGroup.of(product, target.getProductCategory(), brief));
        target.setProductName(ClaimTexts.clip(product, 255));
        target.setSymptomBrief(ClaimTexts.clip(brief, 255));
        target.setSymptomCategory(ClaimSymptomCategory.classify(brief, product, target.getProductCategory(),
                target.getComplaintType(), content));
        target.setClaimOpinion(ClaimHistoryExcel.text(row, "理赔意见"));
        target.setClaimProduct(ClaimTexts.clip(ClaimHistoryExcel.text(row, "理赔产品"), 500));
        target.setJudgment(ClaimTexts.clip(ClaimHistoryExcel.text(row, "初步判定"), 64));
        target.setThirdPartyAmount(ClaimTexts.clip(ClaimHistoryExcel.text(row, "第三方承担"), 32));
        target.setFundAmount(ClaimTexts.clip(ClaimHistoryExcel.text(row, "理赔基金承担"), 32));
        target.setDutyAmount(ClaimTexts.clip(ClaimHistoryExcel.text(row, "追责承担"), 32));
        target.setFreight(ClaimTexts.clip(ClaimHistoryExcel.text(row, "此单运费"), 32));
        target.setResultText(ClaimTexts.clip(ClaimHistoryExcel.text(row, "结果"), 64));
        target.setAdminResult(ClaimHistoryExcel.text(row, "行政部处理结果"));
        target.setDutyDept(ClaimTexts.clip(ClaimHistoryExcel.text(row, "归属责任部门", "归属部门"), 128));
        target.setHandlerName(ClaimTexts.clip(ClaimHistoryExcel.text(row, "行政处理人员"), 64));
        if (target.getManualEdited() == null) {
            target.setManualEdited(0);
        }
        if (found == null) {
            historyMapper.insert(target);
            result.setInserted(result.getInserted() + 1);
        } else {
            historyMapper.updateById(target);
            result.setUpdated(result.getUpdated() + 1);
        }
    }

    /** 只补空着的现象大类和产品分组，已经有值的保留。 */
    public void fillSymptomCategories() {
        List<ClaimHistoryCase> rows = historyMapper.selectList(new LambdaQueryWrapper<ClaimHistoryCase>()
                .and(wrapper -> wrapper.isNull(ClaimHistoryCase::getSymptomCategory)
                        .or().eq(ClaimHistoryCase::getSymptomCategory, "")
                        .or().isNull(ClaimHistoryCase::getProductGroup)
                        .or().eq(ClaimHistoryCase::getProductGroup, "")));
        int updated = 0;
        for (ClaimHistoryCase row : rows) {
            boolean changed = false;
            if (ClaimTexts.blank(row.getSymptomCategory())) {
                row.setSymptomCategory(ClaimSymptomCategory.classify(row.getSymptomBrief(), row.getProductName(),
                        row.getProductCategory(), row.getComplaintType(), row.getComplaintContent()));
                changed = true;
            }
            if (ClaimTexts.blank(row.getProductGroup())) {
                row.setProductGroup(ClaimProductGroup.of(row.getProductName(), row.getProductCategory(), row.getSymptomBrief()));
                changed = true;
            }
            if (changed) {
                historyMapper.updateById(row);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("已补历史案例现象大类和产品分组 {} 条", updated);
        }
    }

    private boolean changed(String previous, String submitted) {
        return !ClaimTexts.empty(previous).equals(ClaimTexts.empty(submitted));
    }

    private String dedupe(String year, String date, String phone, String name, String product, String brief) {
        String raw = year + "|" + date + "|" + phone + "|" + name + "|" + product + "|" + brief;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String alignYear(String value, String year) {
        if (!"2024".equals(year) && !"2025".equals(year)) {
            return value;
        }
        return ClaimTexts.forceYear(value, year);
    }

    private long pageNum(ClaimPageQueryDTO query) {
        return query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
    }

    private long pageSize(ClaimPageQueryDTO query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }
}
