package com.example.sporty.features.exerciseMatching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(MatchController.class)
@Import({MatchService.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes")
class MatchCapacityControllerTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-only-signing-secret-with-at-least-32-bytes");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private MatchRepository matchRepository;
    @MockitoBean private MatchParticipantRepository matchParticipantRepository;
    @MockitoBean private UserClient userClient;
    @MockitoBean private FacilityClient facilityClient;

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("생성 정원이 2 미만이면 필드 오류 400이며 서비스에 접근하지 않는다")
    void createRejectsCapacityBelowTwo(int capacity) throws Exception {
        mvc.perform(post("/api/matches")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(capacity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxParticipant").value("모집 정원은 2명 이상이어야 합니다."));

        verifyNoInteractions(matchRepository, matchParticipantRepository, userClient, facilityClient);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("수정 정원이 2 미만이면 필드 오류 400이며 서비스에 접근하지 않는다")
    void modifyRejectsCapacityBelowTwo(int capacity) throws Exception {
        mvc.perform(put("/api/matches/101")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(capacity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxParticipant").value("모집 정원은 2명 이상이어야 합니다."));

        verifyNoInteractions(matchRepository, matchParticipantRepository, userClient, facilityClient);
    }

    @Test
    @DisplayName("현재 인원 미만으로 정원을 줄이면 400과 전용 오류 코드를 반환한다")
    void modifyRejectsCapacityBelowParticipantsWithErrorCode() throws Exception {
        MatchEntity match = MatchEntity.builder().id(101L).title("기존 제목")
                .maxParticipant(4).status(MatchStatus.RECRUITING).build();
        when(matchRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatch_IdAndUserId(101L, 1L))
                .thenReturn(Optional.of(MatchParticipantEntity.builder()
                        .match(match).userId(1L).role(MatchParticipantRole.OWNER).build()));
        when(matchParticipantRepository.countByMatch_Id(101L)).thenReturn(3L);

        mvc.perform(put("/api/matches/101")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_CAPACITY_BELOW_PARTICIPANTS"))
                .andExpect(jsonPath("$.message").value("모집 정원은 현재 참가 인원 이상이어야 합니다."));

        assertThat(match.getTitle()).isEqualTo("기존 제목");
        assertThat(match.getMaxParticipant()).isEqualTo(4);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        InOrder order = inOrder(matchRepository, matchParticipantRepository);
        order.verify(matchRepository).findByIdForUpdate(101L);
        order.verify(matchParticipantRepository).findByMatch_IdAndUserId(101L, 1L);
        order.verify(matchParticipantRepository).countByMatch_Id(101L);
        verifyNoInteractions(userClient, facilityClient);
    }

    private String request(int capacity) throws Exception {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        return objectMapper.writeValueAsString(Map.of(
                "serviceId", 7L, "title", "수정 제목", "description", "수정 설명",
                "startAt", startAt, "endAt", startAt.plusHours(2),
                "maxParticipant", capacity, "skillLevel", "BEGINNER",
                "sportType", "FUTSAL", "genderGroup", "MIXED"));
    }

    private String bearer() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
