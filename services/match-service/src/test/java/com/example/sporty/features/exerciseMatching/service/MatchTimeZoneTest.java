package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.exerciseMatching.client.*;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.*;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.*;

class MatchTimeZoneTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Los_Angeles"})
    void seoulStartIsUsedForJoinLeaveAndModificationRegardlessOfJvmZone(String defaultZone) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
            MatchRepository matches = mock(MatchRepository.class);
            MatchParticipantRepository participants = mock(MatchParticipantRepository.class);
            UserClient users = mock(UserClient.class);
            FacilityClient facilities = mock(FacilityClient.class);
            MatchService service = new MatchService(matches, participants, users, facilities,
                    mock(PointClient.class), mock(MatchUserGuard.class), mock(ContributionRefundService.class));
            LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1);
            MatchEntity match = MatchEntity.builder().id(1L).serviceId(1L).title("서울 경기")
                    .startAt(start).endAt(start.plusHours(2)).maxParticipant(3).build();
            when(matches.findByIdForUpdate(1L)).thenReturn(Optional.of(match));
            when(users.findUser(2L)).thenReturn(Optional.of(new UserClient.UserSummary(2L, "ACTIVE")));
            assertThatThrownBy(() -> service.joinMatch(1L, 2L)).isInstanceOf(MatchAlreadyStartedException.class);

            match.closeRecruitment();
            MatchParticipantEntity participant = MatchParticipantEntity.builder().match(match).userId(2L)
                    .role(MatchParticipantRole.PARTICIPANT).build();
            when(participants.findByMatch_IdAndUserId(1L, 2L)).thenReturn(Optional.of(participant));
            when(participants.countByMatch_Id(1L)).thenReturn(1L);
            service.leaveMatch(1L, 2L);
            assertThat(match.getStatus()).isEqualTo(MatchStatus.CLOSED);

            when(participants.findByMatch_IdAndUserId(1L, 1L)).thenReturn(Optional.of(
                    MatchParticipantEntity.builder().match(match).userId(1L).role(MatchParticipantRole.OWNER).build()));
            when(facilities.findService(1L)).thenReturn(Optional.empty());
            service.modifyMatch(1L, 1L, MatchModifyRequestDto.builder().title("서울 경기")
                    .startAt(start).endAt(start.plusHours(2)).maxParticipant(4)
                    .skillLevel(SkillLevel.BEGINNER).genderGroup(GenderGroup.MIXED).build());
            assertThat(match.getStatus()).isEqualTo(MatchStatus.CLOSED);
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
