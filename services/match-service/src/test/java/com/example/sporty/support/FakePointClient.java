package com.example.sporty.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.exerciseMatching.client.PointClient;

/** 포인트 서비스 대신 잔액을 메모리에 보관한다. 기본 잔액은 테스트가 충분히 크게 둔다. */
public class FakePointClient implements PointClient {

    public static final long DEFAULT_BALANCE = 1_000_000L;
    private final Map<Long, Long> balances = new ConcurrentHashMap<>();
    private final Map<Long, Integer> refundCallCounts = new ConcurrentHashMap<>();
    private final java.util.Set<Long> refundFailuresFor = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final Map<String, Boolean> debitStates = new ConcurrentHashMap<>();
    private final Map<String, Long> refunded = new ConcurrentHashMap<>();
    private final java.util.Set<Long> debitResponseLoss = ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> refundResponseLoss = ConcurrentHashMap.newKeySet();
    public void loseNextDebitResponseFor(Long userId) { debitResponseLoss.add(userId); }
    public void loseNextRefundResponseFor(Long userId) { refundResponseLoss.add(userId); }

    public void setBalance(Long userId, Long balance) {
        balances.put(userId, balance);
    }

    // 여러 참가자를 순서대로 환불하다 한 명이 실패하는 상황을 재현한다.
    public void failNextRefundFor(Long userId) {
        refundFailuresFor.add(userId);
    }

    public Long getBalance(Long userId) {
        return balances.getOrDefault(userId, DEFAULT_BALANCE);
    }

    // 중복 환불(이중 지급) 버그를 테스트에서 잡기 위해 refund 호출 횟수를 센다.
    public int getRefundCallCount(Long userId) {
        return refundCallCounts.getOrDefault(userId, 0);
    }

    @Override
    public Long deduct(Long userId, Long amount) {
        long balance = balances.getOrDefault(userId, DEFAULT_BALANCE);
        if (balance < amount) {
            throw new InsufficientPointsException();
        }
        long updated = balance - amount;
        balances.put(userId, updated);
        return updated;
    }

    @Override
    public Long refund(Long userId, Long amount) {
        if (refundFailuresFor.remove(userId)) {
            throw new ExternalServiceUnavailableException("point-service");
        }
        refundCallCounts.merge(userId, 1, Integer::sum);
        long updated = balances.getOrDefault(userId, DEFAULT_BALANCE) + amount;
        balances.put(userId, updated);
        return updated;
    }
    @Override
    public synchronized Long deduct(Long userId, Long amount, String operationId) {
        if (debitStates.containsKey(operationId)) {
            if (!debitStates.get(operationId)) throw new ExternalServiceUnavailableException("point-service");
            return getBalance(userId);
        }
        Long balance = deduct(userId, amount);
        debitStates.put(operationId, true);
        if (debitResponseLoss.remove(userId)) throw new ExternalServiceUnavailableException("point-service");
        return balance;
    }

    @Override
    public synchronized Long refund(Long userId, Long amount, String operationId) {
        if (refunded.containsKey(operationId)) return refunded.get(operationId);
        Long balance = refund(userId, amount);
        refunded.put(operationId, balance);
        if (refundResponseLoss.remove(userId)) throw new ExternalServiceUnavailableException("point-service");
        return balance;
    }

    @Override
    public synchronized Long cancel(Long userId, Long amount, String operationId) {
        if (refundFailuresFor.remove(userId)) throw new ExternalServiceUnavailableException("point-service");
        if (Boolean.TRUE.equals(debitStates.get(operationId))) {
            refund(userId, amount);
        }
        debitStates.put(operationId, false);
        if (refundResponseLoss.remove(userId)) throw new ExternalServiceUnavailableException("point-service");
        return getBalance(userId);
    }
}
