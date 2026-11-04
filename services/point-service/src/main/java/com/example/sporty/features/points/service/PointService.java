package com.example.sporty.features.points.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.points.InsufficientPointsException;
import com.example.sporty.features.points.domain.entity.PointAccountEntity;
import com.example.sporty.features.points.repository.PointAccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
SAGA 패턴 시연용 최소 포인트 서비스다. 실제 결제·충전은 다루지 않는다.
계정이 없는 userId는 최초 조회·차감 시 데모용 초기 잔액으로 만든다 (회원가입 이벤트 연동 없음).
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointAccountRepository pointAccountRepository;

    @Value("${point.initial-balance:10000}")
    private Long initialBalance;

    @Transactional
    public Long deduct(Long userId, Long amount) {
        PointAccountEntity account = findOrCreateForUpdate(userId);
        if (!account.hasAtLeast(amount)) {
            throw new InsufficientPointsException();
        }
        account.deduct(amount);
        log.info("포인트 차감: userId={}, amount={}, balance={}", userId, amount, account.getBalance());
        return account.getBalance();
    }

    @Transactional
    public Long refund(Long userId, Long amount) {
        PointAccountEntity account = findOrCreateForUpdate(userId);
        account.refund(amount);
        log.info("포인트 환불: userId={}, amount={}, balance={}", userId, amount, account.getBalance());
        return account.getBalance();
    }

    @Transactional(readOnly = true)
    public Long getBalance(Long userId) {
        return pointAccountRepository.findByUserId(userId)
                .map(PointAccountEntity::getBalance)
                .orElse(initialBalance);
    }

    private PointAccountEntity findOrCreateForUpdate(Long userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> pointAccountRepository.save(
                        PointAccountEntity.builder().userId(userId).balance(initialBalance).build()));
    }
}
