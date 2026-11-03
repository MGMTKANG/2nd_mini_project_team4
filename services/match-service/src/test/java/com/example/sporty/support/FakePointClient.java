package com.example.sporty.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.exerciseMatching.client.PointClient;

/** 포인트 서비스 대신 잔액을 메모리에 보관한다. 기본 잔액은 테스트가 충분히 크게 둔다. */
public class FakePointClient implements PointClient {

    private static final long DEFAULT_BALANCE = 1_000_000L;
    private final Map<Long, Long> balances = new ConcurrentHashMap<>();
    private final Map<Long, Integer> refundCallCounts = new ConcurrentHashMap<>();

    public void setBalance(Long userId, Long balance) {
        balances.put(userId, balance);
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
        refundCallCounts.merge(userId, 1, Integer::sum);
        long updated = balances.getOrDefault(userId, DEFAULT_BALANCE) + amount;
        balances.put(userId, updated);
        return updated;
    }
}
