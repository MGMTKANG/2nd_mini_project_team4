package com.example.sporty.features.exerciseMatching.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;

@ExtendWith(MockitoExtension.class)
class FundingRefundRecoverySchedulerTest {
    @Mock private MatchRepository matches;
    @Mock private FundingRefundService refunds;
    @InjectMocks private FundingRefundRecoveryScheduler scheduler;

    @Test
    void failureDoesNotStarveLaterMatchesAndWrapsToRetryEarlierFailures() {
        when(matches.findFundingRefundPendingIdsAfter(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L), List.of(1L));
        when(matches.findFundingRefundPendingIdsAfter(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(3L));
        when(matches.findFundingRefundPendingIdsAfter(eq(3L), any(Pageable.class)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("temporary point outage")).when(refunds).refundAll(1L);

        scheduler.recover();
        scheduler.recover();
        scheduler.recover();

        verify(refunds, times(2)).refundAll(1L);
        verify(refunds).refundAll(2L);
        verify(refunds).refundAll(3L);
        verify(matches, times(2)).findFundingRefundPendingIdsAfter(0L, PageRequest.of(0, 50));
    }

    @Test
    void emptySweepDoesNotCallRefundService() {
        when(matches.findFundingRefundPendingIdsAfter(eq(0L), any(Pageable.class))).thenReturn(List.of());
        scheduler.recover();
        verifyNoInteractions(refunds);
    }
}
