package com.example.sporty.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.exerciseMatching.client.PointClient;

/** 포인트 서비스 대신 잔액을 메모리에 보관한다. 기본 잔액은 테스트가 충분히 크게 둔다. */
public class FakePointClient implements PointClient {

    private static final long DEFAULT_BALANCE = 1_000_000L;
    private final Map<Long, Long> balances = new ConcurrentHashMap<>();

    public void setBalance(Long userId, Long balance) {
        balances.put(userId, balance);
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
        long updated = balances.getOrDefault(userId, DEFAULT_BALANCE) + amount;
        balances.put(userId, updated);
        return updated;
    }
}
