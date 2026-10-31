package com.example.sporty.features.exerciseMatching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.client.feign.FeignFacilityClient;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.support.LocalHttpStub;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.RetryableException;

// 중지한 시설 스텁의 URL을 독립 컨텍스트에서 사용해 다른 HTTP 테스트에 영향을 주지 않는다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-modify-connection-failure;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.cloud.openfeign.client.config.default.connect-timeout=1000",
        "spring.cloud.openfeign.client.config.default.read-timeout=1500"
})
@AutoConfigureMockMvc
class MatchModifyConnectionFailureHttpIntegrationTest {

    private static final LocalHttpStub USER_SERVICE = new LocalHttpStub();
    private static final String CLOSED_FACILITY_URL = closedFacilityUrl();
    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MatchRepository matchRepository;
    @Autowired private MatchParticipantRepository participantRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private FeignFacilityClient facilityClient;

    @DynamicPropertySource
    static void clientUrls(DynamicPropertyRegistry registry) {
        registry.add("clients.user-service.url", USER_SERVICE::baseUrl);
        registry.add("clients.facility-service.url", () -> CLOSED_FACILITY_URL);
    }

    @AfterAll
    static void stopUserStub() {
        USER_SERVICE.close();
    }

    @Test
    void modifyFacilityConnectionRefusedCommitsAndClearsDecorations() throws Exception {
        USER_SERVICE.stub("/internal/users/profiles", 200, """
                [{"userId":1,"profileId":10,"nickname":"방장","imageUrl":"/owner.png"},
                 {"userId":2,"profileId":20,"nickname":"참가자","imageUrl":"/participant.png"}]
                """);
        LocalDateTime originalStart = LocalDateTime.now().plusDays(1).withNano(0);
        Long matchId = new TransactionTemplate(transactionManager).execute(transaction -> {
            MatchEntity match = matchRepository.save(MatchEntity.builder()
                    .title("기존 제목").description("기존 설명").serviceId(1L)
                    .startAt(originalStart).endAt(originalStart.plusHours(2)).maxParticipant(3)
                    .sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).build());
            participantRepository.saveAll(List.of(
                    MatchParticipantEntity.builder().match(match).userId(1L).role(MatchParticipantRole.OWNER).build(),
                    MatchParticipantEntity.builder().match(match).userId(2L).role(MatchParticipantRole.PARTICIPANT).build()));
            return match.getId();
        });
        AtomicReference<ExternalServiceUnavailableException> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod(); // 실제 Feign이 닫힌 포트에 연결한다.
            } catch (ExternalServiceUnavailableException exception) {
                failure.set(exception);
                throw exception;
            }
        }).when(facilityClient).findServices(anyCollection());
        LocalDateTime startAt = originalStart.plusDays(1);
        MatchModifyRequestDto request = MatchModifyRequestDto.builder()
                .title("연결 거부 중 수정").description("커밋할 설명")
                .startAt(startAt).endAt(startAt.plusHours(3)).maxParticipant(2)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();

        mvc.perform(put("/api/matches/{id}", matchId)
                        .header("Authorization", "Bearer " + new JwtProvider(SECRET).createAccessToken(1L))
                        .contentType("application/json").content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(request.getTitle()))
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceName").value(nullValue()))
                .andExpect(jsonPath("$.locationName").value(nullValue()))
                .andExpect(jsonPath("$.region").value(nullValue()))
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.isParticipant").value(true))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.participants[1].role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.participants[0].profileId").value(nullValue()))
                .andExpect(jsonPath("$.participants[0].nickname").value(nullValue()))
                .andExpect(jsonPath("$.participants[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.participants[1].profileId").value(nullValue()))
                .andExpect(jsonPath("$.participants[1].nickname").value(nullValue()))
                .andExpect(jsonPath("$.participants[1].imageUrl").value(nullValue()));

        assertThat(USER_SERVICE.requests()).extracting(LocalHttpStub.Request::path)
                .containsExactly("/internal/users/profiles");
        assertThat(failure.get()).isNotNull().hasCauseInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo(request.getTitle());
        assertThat(stored.getDescription()).isEqualTo(request.getDescription());
        assertThat(stored.getStartAt()).isEqualTo(request.getStartAt());
        assertThat(stored.getEndAt()).isEqualTo(request.getEndAt());
        assertThat(stored.getMaxParticipant()).isEqualTo(2);
        assertThat(stored.getSkillLevel()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(stored.getGenderGroup()).isEqualTo(GenderGroup.FEMALE);
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(2);
    }

    private static String closedFacilityUrl() {
        try (LocalHttpStub facility = new LocalHttpStub()) {
            return facility.baseUrl();
        }
    }
}
