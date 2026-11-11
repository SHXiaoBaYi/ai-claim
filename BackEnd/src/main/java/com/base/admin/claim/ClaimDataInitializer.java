package com.base.admin.claim;

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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class ClaimDataInitializer implements ApplicationRunner {

    private final ClaimFileStorage fileStorage;
    private final ClaimSopService sopService;
    private final ClaimHistoryService historyService;

    @Override
    public void run(ApplicationArguments args) {
        fileStorage.ensureRoot();
        sopService.seedIfEmpty();
        sopService.patchKeywordTraps();
        try {
            historyService.importIfEmpty();
        } catch (Exception ex) {
            log.warn("历史案例尚未导入：{}", ex.getMessage());
        }
    }
}
