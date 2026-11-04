package com.example.sporty.features.exerciseMatching.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.repository.PointRefundRepository;

/** Local deletion and durable refund intent commit together; no remote credit before that commit. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionRefundService {
    private final PointRefundRepository repository;
    private final PointRefundProcessor processor;

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundContribution(MatchParticipantEntity participant) {
        Long amount = participant.getContributionPoints();
        if (amount == null || amount <= 0) return;
        boolean legacy = participant.getPointOperationId() == null;
        String id = legacy ? UUID.nameUUIDFromBytes(
                ("legacy-match-refund:" + participant.getId()).getBytes(StandardCharsets.UTF_8)).toString()
                : participant.getPointOperationId();
        repository.save(new PointRefundEntity(id, participant.getUserId(), amount, legacy ? "REFUND" : "CANCEL"));
        participant.clearContribution();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { processor.process(id); }
                catch (RuntimeException failure) {
                    // Local action is committed; report acceptance and retry the durable outbox later.
                    log.warn("포인트 환불 재시도 대기: operationId={}, error={}", id, failure.getClass().getSimpleName());
                }
            }
        });
    }
}
