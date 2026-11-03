package com.example.sporty.features.exerciseMatching.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;

import lombok.RequiredArgsConstructor;

/**
 * 참가자 한 명의 환불을 호출자의 트랜잭션과 분리된 새 트랜잭션에서 커밋한다.
 * failFunding처럼 여러 참가자를 순서대로 환불하다가 한 명에서 실패해도, 이미
 * point-service에 커밋된 앞선 환불까지 로컬 트랜잭션 롤백으로 "안 된 것처럼" 되돌아가지
 * 않게 하기 위함이다 (UserWithdrawnHandler와 같은 REQUIRES_NEW 패턴).
 */
@Service
@RequiredArgsConstructor
public class ContributionRefundService {
    private final PointClient pointClient;
    private final MatchParticipantRepository matchParticipantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundContribution(MatchParticipantEntity participant) {
        if (participant.getContributionPoints() == null || participant.getContributionPoints() <= 0) {
            return;
        }
        pointClient.refund(participant.getUserId(), participant.getContributionPoints());
        participant.clearContribution();
        matchParticipantRepository.save(participant);
    }
}
