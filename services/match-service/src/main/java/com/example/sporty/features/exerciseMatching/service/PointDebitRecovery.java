package com.example.sporty.features.exerciseMatching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.repository.PointDebitRepository;

@Service
@RequiredArgsConstructor
public class PointDebitRecovery {
    private final PointDebitRepository repository;
    private final PointClient pointClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelPending(String operationId) {
        // A live match transaction holds this same lock until its commit/rollback.
        var debit = repository.findForUpdate(operationId).orElse(null);
        if (debit == null || !debit.isPending()) return;
        // Also creates a tombstone if the delayed debit has not reached point-service yet.
        pointClient.cancel(debit.getUserId(), debit.getAmount(), operationId);
        debit.cancelled();
    }
}
