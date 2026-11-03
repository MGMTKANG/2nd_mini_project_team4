package com.example.sporty.features.exerciseMatching.service;

import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.UserClient.ProfileSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;
import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakePointClient;
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.MatchDatabaseCleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.support.MatchUserFixtures;
import com.example.sporty.support.FacilityFixtures;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.exerciseMatching.FundingNotFailedException;
import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFundedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyJoinedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchFullException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({MatchService.class, FakeClientsConfig.class})
// 각 서비스 호출이 실제로 커밋/롤백된 뒤 별도 트랜잭션에서 결과를 확인한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchJoinIntegrationTest {

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FakeUserClient fakeUserClient;

    @Autowired
    private FakeFacilityClient fakeFacilityClient;

    @Autowired
    private FakePointClient fakePointClient;

    private Map<Long, Long> users;
    private Long serviceId;

    private Long userId(long label) {
        return users.get(label);
    }

    @BeforeEach
    void setUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users = MatchUserFixtures.create(fakeUserClient, 11);
            serviceId = FacilityFixtures.create(fakeFacilityClient, FacilityFixtures.newServiceId()).id();
        });
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    @DisplayName("[EM06-001] 참가 정보가 실제 DB에 저장되고 상세 조회의 인원과 참여 여부에 반영된다")
    void participationIsCommittedAndVisibleInDetail() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        Long otherMatchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        MatchParticipantResponseDto result = matchService.joinMatch(matchId, userId(2L));

        MatchParticipantEntity saved = matchParticipantRepository.findById(result.getMatchParticipantId()).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(userId(2L));
        assertThat(saved.getRole()).isEqualTo(MatchParticipantRole.PARTICIPANT);
        assertThat(result.getMatchId()).isEqualTo(matchId);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertState(matchId, 2, MatchStatus.RECRUITING);
        assertState(otherMatchId, 1, MatchStatus.RECRUITING);
        var detail = matchService.getMatchDetail(matchId, userId(2L));
        assertThat(detail.getCurrentParticipantCount()).isEqualTo(2);
        assertThat(detail.getIsParticipant()).isTrue();
        assertThat(detail.getIsOwner()).isFalse();
    }

    @Test
    @DisplayName("OWNER 포함 10번째 참가자를 저장하면 CLOSED가 커밋되고 이후 신청은 거절된다")
    void tenthParticipantClosesRecruitment() {
        Long matchId = createMatch(10, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        for (long userId = 2; userId <= 9; userId++) {
            matchService.joinMatch(matchId, userId(userId));
        }
        assertState(matchId, 9, MatchStatus.RECRUITING);

        matchService.joinMatch(matchId, userId(10L));

        assertState(matchId, 10, MatchStatus.CLOSED);
        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(11L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);
        assertState(matchId, 10, MatchStatus.CLOSED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    @DisplayName("[EM06-002/003] OWNER와 일반 참가자의 재신청은 기존 참가 기록과 상태를 유지한다")
    void duplicateParticipationPreservesData(Long userId) {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        matchService.joinMatch(matchId, userId(2L));
        List<Long> ids = participantIds(matchId);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(userId)))
                .isInstanceOf(MatchAlreadyJoinedException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(ids);
        assertState(matchId, 2, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("[EM06-004] RECRUITING이지만 이미 정원이 가득 찬 경우에도 참가 정보가 추가되지 않는다")
    void fullMatchPreservesData() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        // 정원 2에 OWNER와 일반 참가자를 직접 저장해 상태 불일치 방어를 검증한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MatchEntity match = matchRepository.findById(matchId).orElseThrow();
            matchParticipantRepository.save(MatchParticipantEntity.builder()
                    .match(match).userId(userId(2L)).role(MatchParticipantRole.PARTICIPANT).build());
        });
        List<Long> before = participantIds(matchId);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(3L))).isInstanceOf(MatchFullException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(before);
        assertState(matchId, 2, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("[EM06-005] 모집 마감이면 참가 정보와 매치 상태가 유지된다")
    void closedMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.CLOSED, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);

        assertState(matchId, 1, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("경기 시작 이후에는 자리가 남아 있어도 참가 정보가 추가되지 않는다")
    void startedMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(MatchAlreadyStartedException.class);

        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("[EM06-006] 없는 매치의 참여 요청은 다른 매치와 참가 기록을 변경하지 않는다")
    void missingMatchPreservesData() {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.joinMatch(Long.MAX_VALUE, userId(2L)))
                .isInstanceOf(MatchNotFoundException.class);

        assertState(matchId, 1, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("참가자 저장과 CLOSED 변경 SQL 실행 후 트랜잭션 실패 시 두 변경 모두 롤백된다")
    void transactionFailureRollsBackParticipationAndClosure() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchService.joinMatch(matchId, userId(2L));
            matchRepository.flush();
            assertState(matchId, 2, MatchStatus.CLOSED);
            throw new IllegalStateException("test-only transaction failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("test-only transaction failure");

        assertState(matchId, 1, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.existsByMatch_IdAndUserId(matchId, userId(2L))).isFalse();
    }

    @Test
    @DisplayName("마지막 한 자리에 두 사용자가 동시에 신청해도 한 명만 저장되고 모집이 마감된다")
    void concurrentRequestsCannotExceedCapacity() throws Exception {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        List<Object> results = joinConcurrently(matchId, 2L, 3L);

        assertThat(results.stream().filter(MatchParticipantResponseDto.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(MatchRecruitmentClosedException.class::isInstance).count()).isEqualTo(1);
        assertState(matchId, 2, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 두 번 신청해도 참가 기록은 한 개만 저장된다")
    void concurrentDuplicateRequestsCreateOnlyOneParticipant() throws Exception {
        Long matchId = createMatch(5, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        List<Object> results = joinConcurrently(matchId, 2L, 2L);

        assertThat(results.stream().filter(MatchParticipantResponseDto.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(MatchAlreadyJoinedException.class::isInstance).count()).isEqualTo(1);
        assertState(matchId, 2, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .filteredOn(participant -> participant.getUserId().equals(userId(2L))).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 신청은 참가 기록과 모집 상태를 변경하지 않는다")
    void missingUserPreservesData() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        List<Long> before = participantIds(matchId);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, Long.MAX_VALUE))
                .isInstanceOf(MatchUserNotFoundException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(before);
        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("탈퇴 회원의 신청은 참가 기록과 모집 상태를 변경하지 않는다")
    void withdrawnUserPreservesData() {
        Long matchId = createMatch(2, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        List<Long> before = participantIds(matchId);
        fakeUserClient.withdraw(userId(2L));

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(WithdrawnUserFoundException.class);

        assertThat(participantIds(matchId)).containsExactlyElementsOf(before);
        assertThat(matchParticipantRepository.existsByMatch_IdAndUserId(matchId, userId(2L))).isFalse();
        assertState(matchId, 1, MatchStatus.RECRUITING);
    }

    private List<Object> joinConcurrently(Long matchId, Long firstUserId, Long secondUserId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> joinAfterSignal(matchId, firstUserId, ready, start));
            Future<Object> second = executor.submit(() -> joinAfterSignal(matchId, secondUserId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Object joinAfterSignal(Long matchId, Long userId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호 대기 시간 초과");
        }
        try {
            return matchService.joinMatch(matchId, userId(userId));
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    // 생성자도 1인당 참가비를 낸다는 실제 createMatch()의 정책을 그대로 반영한 테스트 픽스처.
    private Long createFundedMatch(int maxParticipant, Long targetPoints) {
        long ownerContribution = (long) Math.ceil(targetPoints / (double) maxParticipant);
        fakePointClient.deduct(users.get(1L), ownerContribution);
        return new TransactionTemplate(transactionManager).execute(status -> {
            MatchEntity match = matchRepository.save(MatchEntity.builder()
                    .title("펀딩 참여 테스트").sportType(SportType.FUTSAL).maxParticipant(maxParticipant)
                    .serviceId(serviceId).genderGroup(GenderGroup.MIXED).targetPoints(targetPoints)
                    .status(MatchStatus.RECRUITING).startAt(LocalDateTime.now().plusDays(1))
                    .endAt(LocalDateTime.now().plusDays(1).plusHours(2)).build());
            matchParticipantRepository.save(MatchParticipantEntity.builder()
                    .match(match).userId(users.get(1L)).role(MatchParticipantRole.OWNER)
                    .contributionPoints(ownerContribution).build());
            return match.getId();
        });
    }

    @Test
    @DisplayName("크라우드펀딩 매치에 참가하면 1인당 참가비가 차감되고 contributionPoints에 기록된다")
    void joinFundedMatchDeductsEntryPointsAndRecordsContribution() {
        Long matchId = createFundedMatch(4, 10000L);
        fakePointClient.setBalance(userId(2L), 5000L);

        matchService.joinMatch(matchId, userId(2L));

        // ceil(10000 / 4) = 2500
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(2500L);
        MatchParticipantEntity participant = matchParticipantRepository
                .findByMatch_IdAndUserId(matchId, userId(2L)).orElseThrow();
        assertThat(participant.getContributionPoints()).isEqualTo(2500L);
    }

    @Test
    @DisplayName("포인트가 부족하면 참가가 거부되고 참가 기록이 생성되지 않는다")
    void joinFundedMatchRejectsWhenPointsInsufficient() {
        Long matchId = createFundedMatch(4, 10000L);
        fakePointClient.setBalance(userId(2L), 1000L);

        assertThatThrownBy(() -> matchService.joinMatch(matchId, userId(2L)))
                .isInstanceOf(InsufficientPointsException.class);

        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(2L))).isEmpty();
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(1000L);
    }

    @Test
    @DisplayName("일반 매치(targetPoints 없음) 참가는 포인트를 차감하지 않는다")
    void joinNonFundedMatchDoesNotTouchPoints() {
        Long matchId = createMatch(4, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));
        fakePointClient.setBalance(userId(2L), 1000L);

        matchService.joinMatch(matchId, userId(2L));

        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(1000L);
    }

    @Test
    @DisplayName("failFunding은 참가비를 낸 참가자 전원에게 환불하고 매치를 마감한다")
    void failFundingRefundsAllContributionsAndClosesMatch() {
        Long matchId = createFundedMatch(4, 10000L);
        fakePointClient.setBalance(userId(2L), 5000L);
        fakePointClient.setBalance(userId(3L), 5000L);
        matchService.joinMatch(matchId, userId(2L));
        matchService.joinMatch(matchId, userId(3L));
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(2500L);
        assertThat(fakePointClient.getBalance(userId(3L))).isEqualTo(2500L);

        matchService.failFunding(matchId);

        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(5000L);
        assertThat(fakePointClient.getBalance(userId(3L))).isEqualTo(5000L);
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(2L))
                .orElseThrow().getContributionPoints()).isNull();

        // 재호출해도(멱등하진 않지만) 이미 clearContribution된 참가자는 다시 환불되지 않는다.
        matchService.failFunding(matchId);
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(5000L);
        assertThat(fakePointClient.getRefundCallCount(userId(2L))).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 다 찬(펀딩 성공) 매치에는 failFunding을 호출할 수 없고 환불도 되지 않는다")
    void failFundingRejectsMatchThatReachedCapacity() {
        // 정원 2명(생성자 포함) 중 생성자도 ceil(4000/2)=2000을 낸 상태로 시작한다.
        Long matchId = createFundedMatch(2, 4000L);
        fakePointClient.setBalance(userId(2L), 5000L);
        matchService.joinMatch(matchId, userId(2L));
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(3000L);
        // 생성자 몫(2000) + 참가자 몫(2000) = 목표 금액(4000)을 정확히 채운다.
        assertThat(fakePointClient.getBalance(userId(1L))).isEqualTo(FakePointClient.DEFAULT_BALANCE - 2000L);

        assertThatThrownBy(() -> matchService.failFunding(matchId))
                .isInstanceOf(FundingNotFailedException.class);

        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(3000L);
        assertThat(fakePointClient.getRefundCallCount(userId(2L))).isZero();
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(2L))
                .orElseThrow().getContributionPoints()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("[회귀] 여러 참가자를 환불하다 한 명이 실패해도 이미 환불된 참가자는 롤백되지 않는다")
    void refundFailureForOneParticipantDoesNotRollBackAlreadyRefundedOthers() {
        Long matchId = createFundedMatch(4, 10000L);
        fakePointClient.setBalance(userId(2L), 5000L);
        fakePointClient.setBalance(userId(3L), 5000L);
        matchService.joinMatch(matchId, userId(2L));
        matchService.joinMatch(matchId, userId(3L));
        fakePointClient.failNextRefundFor(userId(3L));

        assertThatThrownBy(() -> matchService.failFunding(matchId))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        // 생성자(1)와 참가자(2)는 참가자(3) 이전에 각자 독립된 트랜잭션으로 이미 환불·커밋됐다.
        assertThat(fakePointClient.getBalance(userId(1L))).isEqualTo(FakePointClient.DEFAULT_BALANCE);
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(1L))
                .orElseThrow().getContributionPoints()).isNull();
        assertThat(fakePointClient.getBalance(userId(2L))).isEqualTo(5000L);
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(2L))
                .orElseThrow().getContributionPoints()).isNull();

        // 참가자(3)는 환불이 실패했으니 차감된 상태 그대로 남는다.
        assertThat(fakePointClient.getBalance(userId(3L))).isEqualTo(2500L);
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(3L))
                .orElseThrow().getContributionPoints()).isEqualTo(2500L);

        // failFunding() 자체의 트랜잭션은 롤백됐으므로 매치는 아직 마감되지 않았다.
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);

        // 재시도하면 이미 환불된 1·2는 건너뛰고(중복 환불 없음) 3만 환불된다.
        matchService.failFunding(matchId);
        assertThat(fakePointClient.getBalance(userId(3L))).isEqualTo(5000L);
        assertThat(fakePointClient.getRefundCallCount(userId(1L))).isEqualTo(1);
        assertThat(fakePointClient.getRefundCallCount(userId(2L))).isEqualTo(1);
        assertThat(fakePointClient.getRefundCallCount(userId(3L))).isEqualTo(1);
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("[회귀] 크라우드펀딩 매치 생성 시 생성자도 1인당 참가비를 낸다")
    void createMatchDeductsOwnerContributionForFundedMatch() {
        fakePointClient.setBalance(userId(1L), 5000L);

        Long matchId = matchService.createMatch(userId(1L), fundedCreateRequest(4, 10000L));

        // ceil(10000 / 4) = 2500
        assertThat(fakePointClient.getBalance(userId(1L))).isEqualTo(2500L);
        MatchParticipantEntity owner = matchParticipantRepository
                .findByMatch_IdAndUserId(matchId, userId(1L)).orElseThrow();
        assertThat(owner.getContributionPoints()).isEqualTo(2500L);
        assertThat(owner.getRole()).isEqualTo(MatchParticipantRole.OWNER);
    }

    @Test
    @DisplayName("일반 매치(targetPoints 없음) 생성은 생성자의 포인트를 차감하지 않는다")
    void createMatchDoesNotTouchPointsForNonFundedMatch() {
        fakePointClient.setBalance(userId(1L), 5000L);

        Long matchId = matchService.createMatch(userId(1L), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("일반 매치").maxParticipant(4)
                .startAt(LocalDateTime.now().plusDays(1)).endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).build());

        assertThat(fakePointClient.getBalance(userId(1L))).isEqualTo(5000L);
        assertThat(matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId(1L))
                .orElseThrow().getContributionPoints()).isNull();
    }

    @Test
    @DisplayName("생성자 포인트가 부족하면 매치와 참가 기록이 모두 생성되지 않는다")
    void createMatchRejectsWhenOwnerPointsInsufficient() {
        fakePointClient.setBalance(userId(1L), 1000L);

        assertThatThrownBy(() -> matchService.createMatch(userId(1L), fundedCreateRequest(4, 10000L)))
                .isInstanceOf(InsufficientPointsException.class);

        assertThat(fakePointClient.getBalance(userId(1L))).isEqualTo(1000L);
        assertThat(matchRepository.count()).isZero();
    }

    @Test
    @DisplayName("[회귀] 실제 createMatch로 만든 펀딩 매치는 정원이 차면 항상 목표 금액 이상을 모은다")
    void fundedMatchCreatedThroughServiceAlwaysCollectsAtLeastTargetWhenFull() {
        long targetPoints = 10000L;
        int maxParticipant = 4;
        for (long label = 1; label <= maxParticipant; label++) {
            fakePointClient.setBalance(userId(label), 5000L);
        }

        Long matchId = matchService.createMatch(userId(1L), fundedCreateRequest(maxParticipant, targetPoints));
        matchService.joinMatch(matchId, userId(2L));
        matchService.joinMatch(matchId, userId(3L));
        matchService.joinMatch(matchId, userId(4L));

        long totalCollected = matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId).stream()
                .mapToLong(participant -> participant.getContributionPoints() == null
                        ? 0L : participant.getContributionPoints())
                .sum();
        assertThat(totalCollected).isGreaterThanOrEqualTo(targetPoints);
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);

        // 정말로 목표 금액을 채웠으므로, 펀딩 실패 보상 트랜잭션은 거부되어야 한다.
        assertThatThrownBy(() -> matchService.failFunding(matchId))
                .isInstanceOf(FundingNotFailedException.class);
    }

    private MatchCreateRequestDto fundedCreateRequest(int maxParticipant, Long targetPoints) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        return MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("펀딩 매치").maxParticipant(maxParticipant)
                .startAt(startAt).endAt(startAt.plusHours(2))
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED)
                .targetPoints(targetPoints).build();
    }

    @Test
    @DisplayName("크라우드펀딩 매치가 아니면 failFunding을 호출할 수 없다")
    void failFundingRejectsNonFundedMatch() {
        Long matchId = createMatch(4, MatchStatus.RECRUITING, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> matchService.failFunding(matchId))
                .isInstanceOf(MatchNotFundedException.class);

        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
    }

    private Long createMatch(int maxParticipant, MatchStatus matchStatus, LocalDateTime startAt) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            MatchEntity match = matchRepository.save(MatchEntity.builder()
                    .title("참여 테스트").sportType(SportType.FUTSAL).maxParticipant(maxParticipant)
                    .serviceId("S" + serviceId).genderGroup(GenderGroup.MIXED)
                    .status(matchStatus).startAt(startAt).endAt(startAt.plusHours(2)).build());
            matchParticipantRepository.save(MatchParticipantEntity.builder()
                    .match(match).userId(users.get(1L)).role(MatchParticipantRole.OWNER).build());
            return match.getId();
        });
    }

    private List<Long> participantIds(Long matchId) {
        return matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId).stream()
                .map(MatchParticipantEntity::getId).toList();
    }

    private void assertState(Long matchId, long count, MatchStatus status) {
        assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isEqualTo(count);
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(status);
    }
}
