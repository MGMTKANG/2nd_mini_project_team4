package com.example.sporty.features.exerciseMatching.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchUserStateEntity;
import com.example.sporty.features.exerciseMatching.repository.MatchUserStateRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MatchUserGuard {
    private final MatchUserStateRepository states;

    // 모든 쓰기 경로의 순서는 사용자 행 → 매치 행이다. 여러 인스턴스에서도 DB가 직렬화한다.
    public void requireActive(Long userId) {
        if (lock(userId).isWithdrawn()) {
            throw new WithdrawnUserFoundException();
        }
    }

    public void markWithdrawn(Long userId) {
        lock(userId).markWithdrawn();
    }

    private MatchUserStateEntity lock(Long userId) {
        states.ensureRow(userId);
        return states.findByUserIdForUpdate(userId).orElseThrow();
    }
}
