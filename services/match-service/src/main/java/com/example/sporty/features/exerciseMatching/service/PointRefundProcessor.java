package com.example.sporty.features.exerciseMatching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.repository.PointRefundRepository;

@Service
@RequiredArgsConstructor
public class PointRefundProcessor {
    private final PointRefundRepository repository;
    private final PointClient pointClient;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String operationId) {
        var refund = repository.findForUpdate(operationId).orElse(null);
        if (refund == null || !refund.isPending()) return;
        if ("CANCEL".equals(refund.getKind())) {
            pointClient.cancel(refund.getUserId(), refund.getAmount(), operationId);
        } else {
            pointClient.refund(refund.getUserId(), refund.getAmount(), operationId);
        }
        refund.complete();
    }
}
