package com.example.sporty.features.exerciseMatching.service;

import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resume refunds after a process stops between committing failure and finishing all credits. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "match.points.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class FundingRefundRecoveryScheduler {
    private static final int BATCH_SIZE = 50;
    private final MatchRepository matches;
    private final FundingRefundService refunds;
    // Each instance makes its own bounded sweep. The service lock and remote operation ID
    // serialize/reconcile overlap; this cursor is only a fairness optimization, not correctness state.
    private long afterId;

    @Scheduled(initialDelayString = "${match.points.recovery.delay-ms:60000}",
            fixedDelayString = "${match.points.recovery.delay-ms:60000}")
    public void recover() {
        var pending = matches.findFundingRefundPendingIdsAfter(afterId, PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty() && afterId != 0) {
            afterId = 0;
            pending = matches.findFundingRefundPendingIdsAfter(afterId, PageRequest.of(0, BATCH_SIZE));
        }
        for (Long matchId : pending) {
            try {
                refunds.refundAll(matchId);
            } catch (RuntimeException failure) {
                log.warn("펀딩 환불 재시도 실패: matchId={}, error={}", matchId, failure.getClass().getSimpleName());
            } finally {
                // Advance even after failures. Failed rows are retried on the next sweep.
                afterId = matchId;
            }
        }
    }
}
