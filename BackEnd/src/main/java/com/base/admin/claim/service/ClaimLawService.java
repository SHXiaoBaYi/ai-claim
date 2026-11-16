package com.base.admin.claim.service;

import com.base.admin.claim.ClaimPaths;
import com.base.admin.claim.domain.ClaimConsumerLawDoc;
import com.base.admin.claim.mapper.ClaimConsumerLawMapper;
import com.base.admin.claim.support.ClaimConsumerLaw;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimLawService {

    private static final long ROW_ID = 1L;

    private final ClaimConsumerLawMapper lawMapper;
    private final ClaimPaths paths;

    /** 已保存的正文优先；还没保存过时读消协法文件。 */
    public String text() {
        ClaimConsumerLawDoc row = lawMapper.selectById(ROW_ID);
        if (row != null && !ClaimTexts.blank(row.getContent())) {
            return row.getContent().trim();
        }
        return ClaimConsumerLaw.load(paths.historyDir());
    }

    @Transactional
    public void save(String text) {
        String body = ClaimTexts.empty(text).trim();
        if (body.isBlank()) {
            throw new BusinessException("请填写消协法内容");
        }
        ClaimConsumerLawDoc row = lawMapper.selectById(ROW_ID);
        if (row == null) {
            row = new ClaimConsumerLawDoc();
            row.setId(ROW_ID);
            row.setContent(body);
            lawMapper.insert(row);
            return;
        }
        row.setContent(body);
        lawMapper.updateById(row);
    }

    /** 库里还没有正文时，用消协法文件种一条，之后以在线编辑为准。 */
    @Transactional
    public void seedFromFile() {
        ClaimConsumerLawDoc row = lawMapper.selectById(ROW_ID);
        if (row != null && !ClaimTexts.blank(row.getContent())) {
            return;
        }
        String loaded = ClaimConsumerLaw.load(paths.historyDir());
        if (ClaimTexts.blank(loaded)) {
            return;
        }
        if (row == null) {
            row = new ClaimConsumerLawDoc();
            row.setId(ROW_ID);
            row.setContent(loaded);
            lawMapper.insert(row);
            return;
        }
        row.setContent(loaded);
        lawMapper.updateById(row);
    }
}
