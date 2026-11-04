package com.example.sporty.features.exerciseMatching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.repository.PointDebitRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointDebitService {
    private final PointDebitJournal journal;
    private final PointDebitRepository repository;
    private final PointDebitRecovery recovery;
    private final PointClient pointClient;

    @Transactional(propagation = Propagation.MANDATORY)
    public String deduct(Long userId, Long amount) {
        String id = journal.prepare(userId, amount);
        // Atomically commit APPLIED with the participant. Rollback leaves a durable PENDING intent.
        var debit = repository.findForUpdate(id).orElseThrow();
        if (!debit.isPending()) throw new IllegalStateException("Point debit already cancelled");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try { recovery.cancelPending(id); }
                catch (RuntimeException failure) {
                    // The scheduler retries using the same ID, including after a process restart.
                    log.warn("포인트 보상 재시도 대기: operationId={}, error={}", id, failure.getClass().getSimpleName());
                }
            }
        });
        pointClient.deduct(userId, amount, id);
        debit.applied();
        return id;
    }
}
