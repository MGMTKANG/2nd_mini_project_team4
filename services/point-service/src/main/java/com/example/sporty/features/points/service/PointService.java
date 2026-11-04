package com.example.sporty.features.points.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.points.InsufficientPointsException;
import com.example.sporty.features.commons.exception.points.PointOperationCancelledException;
import com.example.sporty.features.points.domain.entity.PointAccountEntity;
import com.example.sporty.features.points.domain.entity.PointOperationEntity;
import com.example.sporty.features.points.repository.PointAccountRepository;
import com.example.sporty.features.points.repository.PointOperationRepository;

import lombok.RequiredArgsConstructor;

/** Demo balances only; no real payment or top-up integration. */
@Service
@RequiredArgsConstructor
public class PointService {
    private final PointAccountRepository pointAccountRepository;
    private final PointOperationRepository pointOperationRepository;

    @Value("${point.initial-balance:10000}")
    private Long initialBalance;

    @Transactional
    public Long deduct(Long userId, Long amount) {
        return deduct(userId, amount, null);
    }

    @Transactional
    public Long deduct(Long userId, Long amount, String operationId) {
        requirePositive(userId, amount);
        PointOperationEntity operation = operationId == null ? null : lockOperation(operationId, userId, amount, "DEBIT");
        if (operation != null) {
            if ("CANCELLED".equals(operation.getStatus())) throw new PointOperationCancelledException();
            if ("DEDUCTED".equals(operation.getStatus())) return operation.getResultBalance();
        }
        PointAccountEntity account = findOrCreateForUpdate(userId);
        if (!account.hasAtLeast(amount)) throw new InsufficientPointsException();
        account.deduct(amount);
        if (operation != null) operation.complete("DEDUCTED", account.getBalance());
        return account.getBalance();
    }

    @Transactional
    public Long refund(Long userId, Long amount) {
        return refund(userId, amount, null);
    }

    /** Standalone credit for contributions made before operation IDs were introduced. */
    @Transactional
    public Long refund(Long userId, Long amount, String operationId) {
        requirePositive(userId, amount);
        PointOperationEntity operation = operationId == null ? null : lockOperation(operationId, userId, amount, "REFUND");
        if (operation != null && "REFUNDED".equals(operation.getStatus())) return operation.getResultBalance();
        PointAccountEntity account = findOrCreateForUpdate(userId);
        account.refund(amount);
        if (operation != null) operation.complete("REFUNDED", account.getBalance());
        return account.getBalance();
    }

    /**
     * Cancel the original debit, even when its HTTP result is unknown. A cancellation received
     * before its debit writes a tombstone, so a delayed debit cannot charge after compensation.
     */
    @Transactional
    public Long cancel(Long userId, Long amount, String operationId) {
        requirePositive(userId, amount);
        PointOperationEntity operation = lockOperation(operationId, userId, amount, "DEBIT");
        if ("CANCELLED".equals(operation.getStatus())) return operation.getResultBalance();
        PointAccountEntity account = findOrCreateForUpdate(userId);
        if ("DEDUCTED".equals(operation.getStatus())) account.refund(amount);
        operation.complete("CANCELLED", account.getBalance());
        return account.getBalance();
    }

    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        return pointAccountRepository.findByUserId(userId).map(PointAccountEntity::getBalance).orElse(initialBalance);
    }

    private PointOperationEntity lockOperation(String operationId, Long userId, Long amount, String kind) {
        if (operationId == null) throw new IllegalArgumentException("operationId는 필수입니다.");
        String id = UUID.fromString(operationId).toString();
        if (!id.equalsIgnoreCase(operationId)) throw new IllegalArgumentException("operationId는 UUID여야 합니다.");
        pointOperationRepository.ensureOperation(id, userId, amount, kind);
        PointOperationEntity operation = pointOperationRepository.findByIdForUpdate(id).orElseThrow();
        operation.requireSameRequest(userId, amount, kind);
        return operation;
    }

    private PointAccountEntity findOrCreateForUpdate(Long userId) {
        pointAccountRepository.ensureAccount(userId, initialBalance);
        return pointAccountRepository.findByUserIdForUpdate(userId).orElseThrow();
    }

    private void requirePositive(Long userId, Long amount) {
        if (userId == null || userId <= 0 || amount == null || amount <= 0) {
            throw new IllegalArgumentException("userId와 amount는 양수여야 합니다.");
        }
    }
}
