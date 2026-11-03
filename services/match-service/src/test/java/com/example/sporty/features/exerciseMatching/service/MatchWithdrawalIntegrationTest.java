package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockingDetails;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakePointClient;
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.MatchUserFixtures;
import com.example.sporty.support.MatchDatabaseCleanup;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-withdrawal;MODE=MariaDB;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MatchService.class, FakeClientsConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchWithdrawalIntegrationTest {

    @Autowired private MatchService matchService;
    @MockitoSpyBean private MatchRepository matchRepository;
    @MockitoSpyBean private MatchParticipantRepository participantRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private FakeUserClient fakeUserClient;
    @Autowired private FakeFacilityClient fakeFacilityClient;
    @Autowired private FakePointClient fakePointClient;

    private Map<Long, Long> users;
    private Long serviceId;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        users = MatchUserFixtures.create(fakeUserClient, 3);
        serviceId = FacilityFixtures.create(fakeFacilityClient, FacilityFixtures.newServiceId()).serviceId();
    }

    @AfterEach
    void tearDown() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void withdrawalDeletesOwnedMatchAndReopensJoinedMatchInIdOrder() {
        Long ownedId = createMatch(1L, 3);
        joinMatch(ownedId, 2L);
        Long joinedId = createMatch(2L, 3);
        joinMatch(joinedId, 1L);
        joinMatch(joinedId, 3L);
        Long unrelatedId = createMatch(3L, 3);
        assertThat(matchRepository.findById(joinedId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        clearInvocations(matchRepository);

        matchService.removeMatchesForWithdrawal(users.get(1L));

        InOrder locks = inOrder(matchRepository);
        locks.verify(matchRepository).findByIdForUpdate(ownedId);
        locks.verify(matchRepository).findByIdForUpdate(joinedId);
        assertThat(matchRepository.existsById(ownedId)).isFalse();
        assertThat(participantRepository.countByMatch_Id(ownedId)).isZero();
        assertThat(participantRepository.findAllByMatch_IdOrderByIdAsc(joinedId))
                .extracting(MatchParticipantEntity::getUserId)
                .containsExactly(users.get(2L), users.get(3L));
        assertThat(participantRepository.findByMatch_IdAndUserId(joinedId, users.get(2L)).orElseThrow().getRole())
                .isEqualTo(MatchParticipantRole.OWNER);
        assertThat(matchRepository.findById(joinedId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(matchRepository.findById(joinedId).orElseThrow().getMaxParticipant()).isEqualTo(3);
        assertThat(matchRepository.existsById(unrelatedId)).isTrue();
        assertThat(participantRepository.countByMatch_Id(unrelatedId)).isEqualTo(1);

        // 중복 정리 및 참가 기록이 없는 회원도 기존 매치에 영향을 주지 않는다.
        matchService.removeMatchesForWithdrawal(users.get(1L));
        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(participantRepository.count()).isEqualTo(3);
    }

    @Test
    void withdrawalKeepsStartedMatchClosed() {
        Long matchId = createMatch(2L, 2);
        joinMatch(matchId, 1L);
        transactions.executeWithoutResult(status -> {
            MatchEntity match = matchRepository.findByIdForUpdate(matchId).orElseThrow();
            LocalDateTime startAt = LocalDateTime.now().minusHours(1).withNano(0);
            match.update(match.getTitle(), match.getDescription(), startAt, startAt.plusHours(2),
                    match.getMaxParticipant(), match.getSkillLevel(), match.getGenderGroup());
        });

        matchService.removeMatchesForWithdrawal(users.get(1L));

        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(1);
        assertThat(participantRepository.existsByMatch_IdAndUserId(matchId, users.get(1L))).isFalse();
        assertThat(matchRepository.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @Test
    void withdrawalPreservesCapacityCommittedAfterIdCollection() throws Exception {
        Long matchId = createMatch(2L, 2);
        joinMatch(matchId, 1L);
        CountDownLatch idsRead = new CountDownLatch(1);
        CountDownLatch continueCleanup = new CountDownLatch(1);
        // 인터페이스의 추상 메서드를 직접 호출하지 않고 실제 Spring Data 프록시로 위임한다.
        Answer<?> repositoryCalls = mockingDetails(participantRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object ids = repositoryCalls.answer(invocation);
            idsRead.countDown();
            if (!continueCleanup.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("탈퇴 정리 재개 시간 초과");
            }
            return ids;
        }).when(participantRepository).findMatchIdsByUserIdOrderByMatchIdAsc(users.get(1L));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        MatchModifyRequestDto request = modification(4);
        Future<?> cleanup = executor.submit(() -> matchService.removeMatchesForWithdrawal(users.get(1L)));
        try {
            awaitSignal(idsRead, cleanup, "탈퇴 정리가 매치 ID 조회를 완료");
            // 정리 트랜잭션은 ID만 읽은 상태. 별도 트랜잭션의 수정이 먼저 커밋된다.
            transactions.executeWithoutResult(status -> matchService.modifyMatch(matchId, users.get(2L), request));
            continueCleanup.countDown();
            cleanup.get(5, TimeUnit.SECONDS);
        } catch (Exception | AssertionError failure) {
            continueCleanup.countDown();
            addWorkerFailure(cleanup, failure);
            throw failure;
        } finally {
            continueCleanup.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(stored.getMaxParticipant()).isEqualTo(4);
        assertThat(stored.getTitle()).isEqualTo(request.getTitle());
        assertThat(stored.getDescription()).isEqualTo(request.getDescription());
        assertThat(stored.getStartAt()).isEqualTo(request.getStartAt());
        assertThat(stored.getEndAt()).isEqualTo(request.getEndAt());
        assertThat(stored.getSkillLevel()).isEqualTo(request.getSkillLevel());
        assertThat(stored.getGenderGroup()).isEqualTo(request.getGenderGroup());
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(participantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .extracting(MatchParticipantEntity::getUserId).containsExactly(users.get(2L));
    }

    private static void awaitSignal(CountDownLatch signal, Future<?> worker, String description) throws Exception {
        boolean signaled = signal.await(5, TimeUnit.SECONDS);
        if (!signaled) {
            try {
                worker.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                throw new AssertionError(description + ": " + exception.getCause(), exception.getCause());
            } catch (TimeoutException exception) {
                throw new AssertionError(description + ": 작업 스레드 시간 초과", exception);
            }
        }
        assertThat(signaled).as(description).isTrue();
    }

    private static void addWorkerFailure(Future<?> worker, Throwable failure) {
        try {
            worker.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            failure.addSuppressed(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure.addSuppressed(exception);
        }
    }

    private Long createMatch(long ownerLabel, int capacity) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        return transactions.execute(status -> matchService.createMatch(users.get(ownerLabel), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("탈퇴 정리 대상").description("기존 설명")
                .startAt(startAt).endAt(startAt.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build()));
    }

    private Long createFundedMatch(long ownerLabel, int capacity, long targetPoints) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        return transactions.execute(status -> matchService.createMatch(users.get(ownerLabel), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("펀딩 탈퇴 정리 대상").description("기존 설명")
                .startAt(startAt).endAt(startAt.plusHours(2)).maxParticipant(capacity).targetPoints(targetPoints)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build()));
    }

    @Test
    void withdrawalRefundsAllParticipantsOfFundedMatchBeforeDeletingIt() {
        Long fundedId = createFundedMatch(1L, 3, 9000L);
        fakePointClient.setBalance(users.get(2L), 5000L);
        joinMatch(fundedId, 2L);
        assertThat(fakePointClient.getBalance(users.get(2L))).isEqualTo(2000L);

        matchService.removeMatchesForWithdrawal(users.get(1L));

        assertThat(fakePointClient.getBalance(users.get(2L))).isEqualTo(5000L);
        assertThat(matchRepository.existsById(fundedId)).isFalse();
    }

    private void joinMatch(Long matchId, long userLabel) {
        transactions.executeWithoutResult(status -> matchService.joinMatch(matchId, users.get(userLabel)));
    }

    private MatchModifyRequestDto modification(int capacity) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
        return MatchModifyRequestDto.builder().title("먼저 커밋한 수정").description("보존할 설명")
                .startAt(startAt).endAt(startAt.plusHours(3)).maxParticipant(capacity)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();
    }
}
