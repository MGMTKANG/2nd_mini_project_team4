package com.example.sporty.features.exerciseMatching.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.sporty.features.exerciseMatching.repository.PointDebitRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "match.points.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PointDebitRecoveryScheduler {
    private final PointDebitRepository repository;
    private final PointDebitRecovery recovery;
    private String cursor = "";
    @Scheduled(initialDelayString = "${match.points.recovery.delay-ms:60000}",
               fixedDelayString = "${match.points.recovery.delay-ms:60000}")
    public synchronized void recover() {
        var pending = repository.findTop50ByStatusAndCreatedAtBeforeAndOperationIdGreaterThanOrderByOperationIdAsc(
                "PENDING", Instant.now().minusSeconds(30), cursor);
        for (var debit : pending) {
            cursor = debit.getOperationId();
            try { recovery.cancelPending(debit.getOperationId()); }
            catch (RuntimeException failure) {
                log.warn("포인트 보상 재시도 실패: operationId={}, error={}",
                        debit.getOperationId(), failure.getClass().getSimpleName());
            }
        }
        if (pending.size() < 50) cursor = "";
    }
}
