package com.example.sporty.features.facilities.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.sporty.features.facilities.service.FacilitySyncService;
import com.example.sporty.features.facilities.service.FacilitySyncService.SyncResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(FacilitySyncScheduler.class);

    private final FacilitySyncService facilitySyncService;
    private final TaskScheduler taskScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        taskScheduler.schedule(() -> synchronize("기동 시"), Instant.now());
    }

    @Scheduled(
            cron = "${facility.sync.cron:0 0 3 * * *}",
            zone = "${facility.sync.zone:Asia/Seoul}"
    )
    public void synchronizeFacilities() {
        synchronize("정기");
    }

    private void synchronize(String trigger) {
        log.info("서울시 체육시설 동기화를 시작합니다. 실행 유형: {}", trigger);

        try {
            SyncResult result = facilitySyncService.syncAll();
            log.info(
                    "{} 동기화 성공: 조회 {}, 신규 {}, 갱신 {}, 비활성화 {}, 제외 {}",
                    trigger,
                    result.fetchedCount(),
                    result.createdCount(),
                    result.updatedCount(),
                    result.deactivatedCount(),
                    result.skippedCount()
            );
        } catch (Exception exception) {
            log.error(
                    "서울시 체육시설 동기화 실패. 실행 유형: {}, 예외: {}",
                    trigger,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
