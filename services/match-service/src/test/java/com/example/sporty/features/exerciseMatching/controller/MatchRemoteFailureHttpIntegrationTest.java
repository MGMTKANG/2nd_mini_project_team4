package com.example.sporty.features.exerciseMatching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.feign.FeignFacilityClient;
import com.example.sporty.features.exerciseMatching.client.feign.FeignUserClient;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.support.LocalHttpStub;
import com.example.sporty.support.MatchDatabaseCleanup;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;

// 테스트 트랜잭션 없이 HTTP 응답 이후 별도 조회하여 실제 커밋/롤백을 검증한다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-remote-failure;MODE=MariaDB",
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
class MatchRemoteFailureHttpIntegrationTest {

    private static final LocalHttpStub USER_SERVICE = new LocalHttpStub();
    private static final LocalHttpStub FACILITY_SERVICE = new LocalHttpStub();
    private static final String FACILITY_JSON = """
            [{"id":1,"serviceId":"S1","serviceName":"풋살장","locationName":"강남체육관","region":"강남구"}]
            """;
    private static final String PROFILES_JSON = """
            [{"userId":1,"profileId":10,"nickname":"방장","imageUrl":"/owner.png"},
             {"userId":2,"profileId":20,"nickname":"참가자","imageUrl":"/participant.png"}]
            """;
    private final JwtProvider jwtProvider = new JwtProvider(
            "test-only-signing-secret-with-at-least-32-bytes");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MatchRepository matchRepository;
    @Autowired private MatchParticipantRepository participantRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserClient userClient;
    @MockitoSpyBean private FeignFacilityClient facilityClient;

    @DynamicPropertySource
    static void clientUrls(DynamicPropertyRegistry registry) {
        registry.add("clients.user-service.url", USER_SERVICE::baseUrl);
        registry.add("clients.facility-service.url", FACILITY_SERVICE::baseUrl);
    }

    @BeforeEach
    void setUp() {
        assertThat(userClient).isInstanceOf(FeignUserClient.class);
        assertThat(facilityClient).isInstanceOf(FeignFacilityClient.class);
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
        USER_SERVICE.reset();
        FACILITY_SERVICE.reset();
        USER_SERVICE.stub("/internal/users/1", 200, "{\"userId\":1,\"status\":\"ACTIVE\"}");
        USER_SERVICE.stub("/internal/users/2", 200, "{\"userId\":2,\"status\":\"ACTIVE\"}");
        USER_SERVICE.stub("/internal/users/profiles", 200, PROFILES_JSON);
        FACILITY_SERVICE.stub("/internal/facility-services", 200, FACILITY_JSON);
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, FACILITY_JSON);
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S1\"]");
    }

    @AfterAll
    static void stopStubs() {
        USER_SERVICE.close();
        FACILITY_SERVICE.close();
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void joinUserServerErrorDoesNotSaveAndAllowsRetry() throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", 500, "{}");

        assertUnavailable(join(matchId), "user-service");
        assertNoNewParticipant(matchId);
        assertThat(USER_SERVICE.requests().stream()
                .filter(request -> request.path().equals("/internal/users/2"))).hasSize(1);

        USER_SERVICE.stub("/internal/users/2", 200, "{\"userId\":2,\"status\":\"ACTIVE\"}");
        // 원격 조회 실패 후 다른 스레드/트랜잭션의 재시도가 성공하는지 확인한다.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                join(matchId).andExpect(status().isCreated())
                        .andExpect(jsonPath("$.userId").value(2));
                return null;
            }).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(2);
    }

    @Test
    void joinUserReadTimeoutDoesNotSave() throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", 200,
                "{\"userId\":2,\"status\":\"ACTIVE\"}", Duration.ofSeconds(3));

        Exception exception = assertUnavailable(join(matchId), "user-service");
        assertThat(exception).hasCauseInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertNoNewParticipant(matchId);
        assertThat(USER_SERVICE.requests().stream()
                .filter(request -> request.path().equals("/internal/users/2"))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\":2}", "{\"status\":\"ACTIVE\"}",
            "{\"userId\":2,\"status\":null}", "null"})
    void joinMissingUserFieldsReturnsUnavailable(String json) throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", 200, json);

        assertUnavailable(join(matchId), "user-service");
        assertNoNewParticipant(matchId);
    }

    @Test
    void joinMalformedUserJsonReturnsUnavailable() throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", 200, "{broken");

        Exception exception = assertUnavailable(join(matchId), "user-service");
        assertThat(exception).hasCauseInstanceOf(DecodeException.class);
        assertNoNewParticipant(matchId);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 429, 502, 503})
    void joinOtherUserHttpErrorsReturnUnavailable(int httpStatus) throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", httpStatus, "{}");

        Exception exception = assertUnavailable(join(matchId), "user-service");
        assertThat(exception.getCause()).isInstanceOf(FeignException.class);
        assertThat(((FeignException) exception.getCause()).status()).isEqualTo(httpStatus);
        assertNoNewParticipant(matchId);
    }

    @Test
    void joinUserNotFoundKeepsExistingUnauthorizedContract() throws Exception {
        Long matchId = createMatch();
        USER_SERVICE.stub("/internal/users/2", 404, "{\"code\":\"USER_NOT_FOUND\"}");

        join(matchId).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MATCH_USER_NOT_FOUND"));
        assertNoNewParticipant(matchId);
    }

    @Test
    void anonymousRegionSearchFacilityServerErrorReturnsUnavailable() throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 500, "{}");

        assertUnavailable(mvc.perform(get("/api/matches").param("region", "강남")), "facility-service");
        assertThat(USER_SERVICE.requests()).isEmpty();
        assertThat(FACILITY_SERVICE.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "강남"})
    void anonymousListFacilityLookupServerErrorReturnsPartialResponse(String region) throws Exception {
        Long matchId = createMatch();
        FACILITY_SERVICE.stub("/internal/facility-services", 500, "{}");

        mvc.perform(get("/api/matches").param("region", region))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchId").value(matchId))
                .andExpect(jsonPath("$[0].title").value("기존 제목"))
                .andExpect(jsonPath("$[0].numCurrentParticipant").value(1))
                .andExpect(jsonPath("$[0].region").value(nullValue()))
                .andExpect(jsonPath("$[0].isFree").value(nullValue()))
                .andExpect(jsonPath("$[0].reservationDeadlineAt").value(nullValue()));
    }

    @Test
    void myMatchesFacilityServerErrorReturnsPartialResponse() throws Exception {
        Long matchId = createMatch();
        FACILITY_SERVICE.stub("/internal/facility-services", 500, "{}");

        mvc.perform(get("/api/matches/me").header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchId").value(matchId))
                .andExpect(jsonPath("$[0].title").value("기존 제목"))
                .andExpect(jsonPath("$[0].numCurrentParticipant").value(1))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].region").value(nullValue()))
                .andExpect(jsonPath("$[0].facilityName").value(nullValue()))
                .andExpect(jsonPath("$[0].isFree").value(nullValue()));
    }

    @Test
    void myMatchesUserLookupServerErrorStillReturnsUnavailable() throws Exception {
        createMatch();
        USER_SERVICE.stub("/internal/users/1", 500, "{}");

        assertUnavailable(mvc.perform(get("/api/matches/me").header("Authorization", bearer(1L))),
                "user-service");
    }

    @Test
    void createUserServerErrorDoesNotSaveMatchOrParticipant() throws Exception {
        USER_SERVICE.stub("/internal/users/1", 500, "{}");

        assertUnavailable(createRequest(), "user-service");
        assertDatabaseEmpty();
        assertThat(FACILITY_SERVICE.requests()).isEmpty();
    }

    @Test
    void createFacilityReadTimeoutDoesNotSaveMatchOrParticipant() throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, FACILITY_JSON, Duration.ofSeconds(3));

        Exception exception = assertUnavailable(createRequest(), "facility-service");
        assertThat(exception).hasCauseInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertDatabaseEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{}]", "[{\"serviceId\":null}]", "[null]", "null",
            "[{\"serviceId\":\"S1\"}]", "[{\"id\":1}]", "[{\"id\":1,\"serviceId\":\"\"}]"})
    void createMissingFacilityIdDoesNotSave(String json) throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, json);

        assertUnavailable(createRequest(), "facility-service");
        assertDatabaseEmpty();
    }

    @Test
    void createMalformedFacilityJsonDoesNotSave() throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, "[broken");

        Exception exception = assertUnavailable(createRequest(), "facility-service");
        assertThat(exception).hasCauseInstanceOf(DecodeException.class);
        assertDatabaseEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void createFacilityHttpErrorDoesNotSave(int httpStatus) throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", httpStatus, "{}");

        Exception exception = assertUnavailable(createRequest(), "facility-service");
        assertThat(((FeignException) exception.getCause()).status()).isEqualTo(httpStatus);
        assertDatabaseEmpty();
    }

    @ParameterizedTest
    @CsvSource({"500,200", "200,500", "500,500"})
    void modifyRemoteServerErrorsCommitAndKeepHealthyDecorations(int profileStatus, int facilityStatus)
            throws Exception {
        Long matchId = createJoinedMatchForModification();
        USER_SERVICE.stub("/internal/users/profiles", profileStatus, profileStatus == 200 ? PROFILES_JSON : "{}");
        FACILITY_SERVICE.stub("/internal/facility-services", facilityStatus,
                facilityStatus == 200 ? FACILITY_JSON : "{}");

        assertModificationCommittedWithPartialDecorations(matchId, modification(), profileStatus == 200,
                facilityStatus == 200);
        assertThat(USER_SERVICE.requests()).extracting(LocalHttpStub.Request::path)
                .containsExactly("/internal/users/profiles");
        assertFacilityLookupRequested();
    }

    @Test
    void modifyFacilityReadTimeoutCommitsAndKeepsProfiles() throws Exception {
        Long matchId = createJoinedMatchForModification();
        FACILITY_SERVICE.stub("/internal/facility-services", 200, FACILITY_JSON, Duration.ofSeconds(3));
        AtomicReference<ExternalServiceUnavailableException> failure = captureFacilityFailure();

        assertModificationCommittedWithPartialDecorations(matchId, modification(), true, false);
        assertFacilityLookupRequested();
        assertThat(failure.get()).isNotNull().hasCauseInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void modifyMalformedFacilityJsonCommitsAndKeepsProfiles() throws Exception {
        Long matchId = createJoinedMatchForModification();
        FACILITY_SERVICE.stub("/internal/facility-services", 200, "[broken");
        AtomicReference<ExternalServiceUnavailableException> failure = captureFacilityFailure();

        assertModificationCommittedWithPartialDecorations(matchId, modification(), true, false);
        assertFacilityLookupRequested();
        assertThat(failure.get()).isNotNull().hasCauseInstanceOf(DecodeException.class);
    }

    private AtomicReference<ExternalServiceUnavailableException> captureFacilityFailure() {
        AtomicReference<ExternalServiceUnavailableException> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod(); // 실제 Feign 호출을 위임하고 발생한 원인만 수집한다.
            } catch (ExternalServiceUnavailableException exception) {
                failure.set(exception);
                throw exception;
            }
        }).when(facilityClient).findServices(anyCollection());
        return failure;
    }

    private void assertModificationCommittedWithPartialDecorations(Long matchId, MatchModifyRequestDto request,
            boolean hasProfiles, boolean hasFacility) throws Exception {
        ResultActions result = modify(matchId, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(request.getTitle()))
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.isParticipant").value(true))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.participants[1].role").value("PARTICIPANT"));
        assertDetailDecorations(result, hasProfiles, hasFacility);

        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(stored.getServiceId()).isEqualTo("S1");
        assertThat(stored.getTitle()).isEqualTo(request.getTitle());
        assertThat(stored.getDescription()).isEqualTo(request.getDescription());
        assertThat(stored.getStartAt()).isEqualTo(request.getStartAt());
        assertThat(stored.getEndAt()).isEqualTo(request.getEndAt());
        assertThat(stored.getMaxParticipant()).isEqualTo(2);
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(stored.getSkillLevel()).isEqualTo(SkillLevel.ADVANCED);
        assertThat(stored.getGenderGroup()).isEqualTo(GenderGroup.FEMALE);
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(2);
    }

    private Long createJoinedMatchForModification() throws Exception {
        Long matchId = createMatch();
        join(matchId).andExpect(status().isCreated());
        // 생성·참가 요청을 지워 수정 응답 조합의 실제 호출만 확인한다.
        USER_SERVICE.reset();
        FACILITY_SERVICE.reset();
        USER_SERVICE.stub("/internal/users/profiles", 200, PROFILES_JSON);
        return matchId;
    }

    private void assertFacilityLookupRequested() {
        assertThat(USER_SERVICE.requests()).extracting(LocalHttpStub.Request::path)
                .containsExactly("/internal/users/profiles");
        assertThat(FACILITY_SERVICE.requests()).hasSize(1);
        LocalHttpStub.Request request = FACILITY_SERVICE.requests().get(0);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/internal/facility-services");
        assertThat(request.query()).containsEntry("ids", List.of("S1"));
    }

    @Test
    void modifyHealthyServicesKeepsDecorations() throws Exception {
        Long matchId = createMatch();

        modify(matchId, modification()).andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceName").value("풋살장"))
                .andExpect(jsonPath("$.participants[0].profileId").value(10))
                .andExpect(jsonPath("$.participants[0].nickname").value("방장"));
        assertThat(matchRepository.findById(matchId).orElseThrow().getTitle()).isEqualTo("수정 제목");
    }

    @Test
    void createStoresSvcIdAndDetailReturnsNumericFacilityId() throws Exception {
        Long matchId = createMatch();

        assertThat(jdbc.queryForObject("SELECT service_id FROM \"match\" WHERE id=?", String.class, matchId))
                .isEqualTo("S1");
        assertThat(FACILITY_SERVICE.requests()).containsExactly(new LocalHttpStub.Request(
                "GET", "/internal/facility-services/by-id", java.util.Map.of("ids", List.of("1"))));
        mvc.perform(get("/api/matches/{id}", matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceName").value("풋살장"))
                .andExpect(jsonPath("$.currentParticipantCount").value(1))
                .andExpect(jsonPath("$.svcId").doesNotExist());
    }

    @Test
    void createMissingNumericFacilityIdReturnsExistingNotFoundAndSavesNothing() throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, "[]");

        createRequest().andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        assertDatabaseEmpty();
    }

    @Test
    void searchNumericFacilityIdFiltersStoredSvcIdAndCombinesFacilityConditions() throws Exception {
        Long matchId = createMatch();
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, """
                [{"id":2,"serviceId":"S2","serviceName":"다른 시설","region":"서초구"}]
                """);
        createRequest(2L).andExpect(status().isCreated());
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, FACILITY_JSON);

        mvc.perform(get("/api/matches").param("serviceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchId").value(matchId));
        mvc.perform(get("/api/matches").param("serviceId", "1").param("region", "강남").param("isFree", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchId").value(matchId));
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S2\"]");
        mvc.perform(get("/api/matches").param("serviceId", "1").param("isFree", "false"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, "[]");
        mvc.perform(get("/api/matches").param("serviceId", "999"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void searchNumericFacilityIdMapsFacilityFailureToUnavailable() throws Exception {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 500, "{}");

        assertUnavailable(mvc.perform(get("/api/matches").param("serviceId", "1")), "facility-service");
    }

    @Test
    void missingFacilityClearsDetailFieldsAndPreservesMatchParticipantsAndPermissions() throws Exception {
        Long matchId = createMatch();
        FACILITY_SERVICE.stub("/internal/facility-services", 200, "[]");

        mvc.perform(get("/api/matches/{id}", matchId).header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.title").value("기존 제목"))
                .andExpect(jsonPath("$.serviceId").value(nullValue()))
                .andExpect(jsonPath("$.serviceName").value(nullValue()))
                .andExpect(jsonPath("$.locationName").value(nullValue()))
                .andExpect(jsonPath("$.region").value(nullValue()))
                .andExpect(jsonPath("$.currentParticipantCount").value(1))
                .andExpect(jsonPath("$.participants[0].profileId").value(10))
                .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                .andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.isParticipant").value(true));
        assertThat(matchRepository.findById(matchId).orElseThrow().getServiceId()).isEqualTo("S1");
    }

    @ParameterizedTest
    @CsvSource({"500,200", "200,500", "500,500"})
    void detailRemoteServerErrorsKeepHealthyDecorationsAndParticipants(int profileStatus, int facilityStatus)
            throws Exception {
        Long matchId = createJoinedMatchForModification();
        USER_SERVICE.stub("/internal/users/profiles", profileStatus, profileStatus == 200 ? PROFILES_JSON : "{}");
        FACILITY_SERVICE.stub("/internal/facility-services", facilityStatus,
                facilityStatus == 200 ? FACILITY_JSON : "{}");

        for (long userId : new long[] {0, 1, 2}) {
            var request = get("/api/matches/{id}", matchId);
            if (userId != 0) request.header("Authorization", bearer(userId));
            ResultActions result = mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matchId").value(matchId))
                    .andExpect(jsonPath("$.title").value("기존 제목"))
                    .andExpect(jsonPath("$.currentParticipantCount").value(2))
                    .andExpect(jsonPath("$.participants.length()").value(2))
                    .andExpect(jsonPath("$.participants[0].role").value("OWNER"))
                    .andExpect(jsonPath("$.participants[1].role").value("PARTICIPANT"))
                    .andExpect(jsonPath("$.isOwner").value(userId == 1))
                    .andExpect(jsonPath("$.isParticipant").value(userId != 0));
            assertDetailDecorations(result, profileStatus == 200, facilityStatus == 200);
        }
        assertThat(FACILITY_SERVICE.requests()).hasSize(3);
        assertThat(USER_SERVICE.requests()).extracting(LocalHttpStub.Request::path)
                .containsExactly("/internal/users/profiles", "/internal/users/profiles", "/internal/users/profiles");
        assertThat(matchRepository.findById(matchId).orElseThrow().getServiceId()).isEqualTo("S1");
    }

    private void assertDetailDecorations(ResultActions result, boolean hasProfiles, boolean hasFacility)
            throws Exception {
        result.andExpect(jsonPath("$.serviceId").value(hasFacility ? is(1) : nullValue()))
                .andExpect(jsonPath("$.serviceName").value(hasFacility ? is("풋살장") : nullValue()))
                .andExpect(jsonPath("$.locationName").value(hasFacility ? is("강남체육관") : nullValue()))
                .andExpect(jsonPath("$.region").value(hasFacility ? is("강남구") : nullValue()))
                .andExpect(jsonPath("$.participants[0].profileId").value(hasProfiles ? is(10) : nullValue()))
                .andExpect(jsonPath("$.participants[0].nickname").value(hasProfiles ? is("방장") : nullValue()))
                .andExpect(jsonPath("$.participants[0].imageUrl").value(hasProfiles ? is("/owner.png") : nullValue()))
                .andExpect(jsonPath("$.participants[1].profileId").value(hasProfiles ? is(20) : nullValue()))
                .andExpect(jsonPath("$.participants[1].nickname").value(hasProfiles ? is("참가자") : nullValue()))
                .andExpect(jsonPath("$.participants[1].imageUrl").value(hasProfiles ? is("/participant.png") : nullValue()));
    }

    private Exception assertUnavailable(ResultActions result, String serviceName) throws Exception {
        Exception exception = result.andExpect(status().isServiceUnavailable())
                .andExpect(content().json(objectMapper.writeValueAsString(java.util.Map.of(
                        "code", "EXTERNAL_SERVICE_UNAVAILABLE",
                        "message", "외부 서비스(" + serviceName + ")를 사용할 수 없습니다."))))
                .andReturn().getResolvedException();
        assertThat(exception).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(((ExternalServiceUnavailableException) exception).getServiceName()).isEqualTo(serviceName);
        return exception;
    }

    private void assertNoNewParticipant(Long matchId) {
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(1);
        assertThat(participantRepository.existsByMatch_IdAndUserId(matchId, 2L)).isFalse();
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
    }

    private void assertDatabaseEmpty() {
        assertThat(matchRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
    }

    private Long createMatch() throws Exception {
        String body = createRequest().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, Long.class);
    }

    private ResultActions createRequest() throws Exception {
        return createRequest(1L);
    }

    private ResultActions createRequest(Long facilityId) throws Exception {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        MatchCreateRequestDto request = MatchCreateRequestDto.builder()
                .serviceId(facilityId).title("기존 제목").description("기존 설명")
                .startAt(startAt).endAt(startAt.plusHours(2)).maxParticipant(3)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).build();
        return mvc.perform(post("/api/matches").header("Authorization", bearer(1L))
                .contentType("application/json").content(objectMapper.writeValueAsBytes(request)));
    }

    private ResultActions join(Long matchId) throws Exception {
        return mvc.perform(post("/api/matches/{id}/participants", matchId).header("Authorization", bearer(2L)));
    }

    private ResultActions modify(Long matchId, MatchModifyRequestDto request) throws Exception {
        return mvc.perform(put("/api/matches/{id}", matchId).header("Authorization", bearer(1L))
                .contentType("application/json").content(objectMapper.writeValueAsBytes(request)));
    }

    private MatchModifyRequestDto modification() {
        LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
        return MatchModifyRequestDto.builder().title("수정 제목").description("수정 설명")
                .startAt(startAt).endAt(startAt.plusHours(3)).maxParticipant(2)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }
}
