package com.example.sporty.features.exerciseMatching.service;

import com.example.sporty.features.commons.exception.exerciseMatching.FundingNotFailedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFundedException;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 환불 요청보다 먼저 모집 종료를 커밋하여 일부 환불 실패에도 재모집을 막는다. */
@Service
@RequiredArgsConstructor
public class FundingFailureService {
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long matchId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        if (match.getTargetPoints() == null) {
            throw new MatchNotFundedException();
        }
        // 실패 후 재시도는 이미 확정된 종료 상태를 유지한다.
        if (match.isFundingFailed()) {
            return;
        }
        if (matchParticipantRepository.countByMatch_Id(matchId) >= match.getMaxParticipant()) {
            throw new FundingNotFailedException();
        }
        match.markFundingFailed();
    }
}
