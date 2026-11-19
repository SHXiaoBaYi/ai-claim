package com.base.admin.claim;

import com.base.admin.claim.service.ClaimArchiveService;
import com.base.admin.claim.service.ClaimLawService;
import com.base.admin.claim.support.ClaimConsumerLaw;
import com.base.admin.claim.service.ClaimFileStorage;
import com.base.admin.claim.service.ClaimHistoryService;
import com.base.admin.claim.service.ClaimSopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class ClaimDataInitializer implements ApplicationRunner {

    private final ClaimFileStorage fileStorage;
    private final ClaimSopService sopService;
    private final ClaimHistoryService historyService;
    private final ClaimArchiveService archiveService;
    private final ClaimLawService lawService;
    private final ClaimPaths paths;

    @Override
    public void run(ApplicationArguments args) {
        fileStorage.ensureRoot();
        lawService.seedFromFile();
        sopService.seedIfEmpty();
        sopService.patchKeywordTraps();
        sopService.patchAdviceRules();
        sopService.ensureCatalogNodes();
        try {
            historyService.importIfEmpty();
            historyService.importMissing();
        } catch (Exception ex) {
            log.warn("历史案例尚未导入（{}）：{}", paths.historyDir().toAbsolutePath(), ex.getMessage());
        }
        historyService.fillSymptomCategories();
        archiveService.fillSymptomCategories();
        Path lawFile = ClaimConsumerLaw.find(paths.historyDir());
        if (lawFile == null) {
            log.warn("没有消协法文件，建议只会使用内置分流：{}", paths.historyDir().toAbsolutePath());
        } else {
            log.info("消协法文件：{}", lawFile.toAbsolutePath());
        }
    }
}
