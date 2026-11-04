package com.example.sporty.features.exerciseMatching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.exerciseMatching.repository.*;

@Service
@RequiredArgsConstructor
public class FundingRefundService {
    private final MatchRepository matches;
    private final MatchParticipantRepository participants;
    private final ContributionRefundService refunds;
    @Transactional
    public void refundAll(Long matchId) {
        var match = matches.findByIdForUpdate(matchId).orElseThrow(MatchNotFoundException::new);
        if (!match.isFundingFailed()) throw new IllegalStateException("Funding must be closed before refunds");
        for (var participant : participants.findAllByMatch_IdOrderByIdAsc(matchId)) {
            refunds.refundContribution(participant);
        }
    }
}
