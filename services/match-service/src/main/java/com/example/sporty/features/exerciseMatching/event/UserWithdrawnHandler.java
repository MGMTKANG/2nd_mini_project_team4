package com.example.sporty.features.exerciseMatching.event;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserWithdrawnHandler {
    public static final String CONSUMER_NAME = "match-service.user-withdrawn";

    private final ProcessedEventRepository processedEventRepository;
    private final MatchService matchService;

    // 리스너에서 별도 프록시로 호출한다. 기존 Match가 없는 새 영속성 컨텍스트를 보장한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void handle(String eventId, Long userId) {
        if (wasProcessed(eventId)) {
            return;
        }
        try {
            processedEventRepository.insert(CONSUMER_NAME, eventId, LocalDateTime.now());
        } catch (DataIntegrityViolationException exception) {
            // 이 트랜잭션 안에서 예외를 삼키면 rollback-only 상태가 된다.
            // 호출자에게 전파해 롤백을 마친 다음 별도 조회로 PK 중복 여부를 확인한다.
            throw new EventClaimException(exception);
        }
        matchService.removeMatchesForWithdrawal(userId);
    }

    public boolean wasProcessed(String eventId) {
        return processedEventRepository.existsById(new ProcessedEventId(CONSUMER_NAME, eventId));
    }

    static final class EventClaimException extends RuntimeException {
        EventClaimException(DataIntegrityViolationException cause) {
            super(cause);
        }

        @Override
        public synchronized DataIntegrityViolationException getCause() {
            return (DataIntegrityViolationException) super.getCause();
        }
    }
}
