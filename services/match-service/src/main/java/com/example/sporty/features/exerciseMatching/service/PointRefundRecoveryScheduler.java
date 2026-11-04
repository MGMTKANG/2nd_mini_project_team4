package com.example.sporty.features.exerciseMatching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.sporty.features.exerciseMatching.repository.PointRefundRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "match.points.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PointRefundRecoveryScheduler {
    private final PointRefundRepository repository;
    private final PointRefundProcessor processor;
    private String cursor = "";
    @Scheduled(initialDelayString = "${match.points.recovery.delay-ms:60000}",
               fixedDelayString = "${match.points.recovery.delay-ms:60000}")
    public synchronized void recover() {
        var pending = repository.findTop50ByStatusAndOperationIdGreaterThanOrderByOperationIdAsc("PENDING", cursor);
        for (var refund : pending) {
            cursor = refund.getOperationId();
            try { processor.process(cursor); }
            catch (RuntimeException failure) {
                log.warn("포인트 환불 재시도 대기: operationId={}, error={}", cursor, failure.getClass().getSimpleName());
            }
        }
        if (pending.size() < 50) cursor = "";
    }
}
