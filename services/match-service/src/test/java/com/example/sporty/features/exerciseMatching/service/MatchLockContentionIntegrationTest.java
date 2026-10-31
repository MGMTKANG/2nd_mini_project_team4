package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.MatchUserFixtures;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-lock-contention;MODE=MariaDB;LOCK_TIMEOUT=10000",
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
class MatchLockContentionIntegrationTest {

    @Autowired private MatchService matchService;
    @MockitoSpyBean private MatchRepository matchRepository;
    @Autowired private MatchParticipantRepository participantRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FakeUserClient fakeUserClient;
    @Autowired private FakeFacilityClient fakeFacilityClient;

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
        transactions.executeWithoutResult(status -> {
            participantRepository.deleteAllInBatch();
            matchRepository.deleteAllInBatch();
        });
    }

    @Test
    void modifyMatchWaitsForLockAndUsesCommittedParticipantCount() throws Exception {
        Long matchId = createMatch(3);

        var response = whileMatchLocked(matchId, match -> addParticipant(match, users.get(2L)),
                () -> matchService.modifyMatch(matchId, users.get(1L), modification(2)));

        assertThat(response.getCurrentParticipantCount()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(MatchStatus.CLOSED);
        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("잠금 후 수정");
        assertThat(stored.getMaxParticipant()).isEqualTo(2);
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(2);
    }

    @Test
    void joinMatchWaitsForLockAndPreservesCommittedMatchChanges() throws Exception {
        Long matchId = createMatch(2);

        var response = whileMatchLocked(matchId, match -> match.update(
                "먼저 커밋한 제목", match.getDescription(), match.getStartAt(), match.getEndAt(),
                match.getMaxParticipant(), match.getSkillLevel(), match.getGenderGroup()),
                () -> matchService.joinMatch(matchId, users.get(2L)));

        assertThat(response.getUserId()).isEqualTo(users.get(2L));
        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("먼저 커밋한 제목");
        assertThat(stored.getMaxParticipant()).isEqualTo(2);
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(participantRepository.countByMatch_Id(matchId)).isEqualTo(2);
    }

    @Test
    void deleteMatchWaitsForLockAndDeletesCommittedParticipants() throws Exception {
        Long matchId = createMatch(3);
        Long otherMatchId = createMatch(3);

        whileMatchLocked(matchId, match -> addParticipant(match, users.get(2L)), () -> {
            matchService.deleteMatch(matchId, users.get(1L));
            return null;
        });

        assertThat(matchRepository.existsById(matchId)).isFalse();
        assertThat(participantRepository.countByMatch_Id(matchId)).isZero();
        assertThat(matchRepository.existsById(otherMatchId)).isTrue();
        assertThat(participantRepository.countByMatch_Id(otherMatchId)).isEqualTo(1);
    }

    private <T> T whileMatchLocked(Long matchId, Consumer<MatchEntity> holderChange, Callable<T> action)
            throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch queryReturned = new CountDownLatch(1);
        AtomicReference<Thread> contenderThread = new AtomicReference<>();

        // JDK 프록시의 인터페이스 메서드는 callRealMethod() 대신 원래 저장소로 위임한다.
        Answer<?> repositoryCalls = mockingDetails(matchRepository).getMockCreationSettings().getDefaultAnswer();
        // 실제 DB 조회는 그대로 실행한다. 일반 조회로 회귀해도 반환 시점을 관찰한다.
        Answer<Object> observeRead = invocation -> {
            boolean contender = Thread.currentThread() == contenderThread.get();
            if (contender) queryEntered.countDown();
            Object result = repositoryCalls.answer(invocation);
            if (contender) queryReturned.countDown();
            return result;
        };
        doAnswer(observeRead).when(matchRepository).findByIdForUpdate(matchId);
        doAnswer(observeRead).when(matchRepository).findById(matchId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = null;
        Future<T> contender = null;
        try {
            holder = executor.submit(() -> transactions
                    .executeWithoutResult(status -> {
                        MatchEntity match = matchRepository.findByIdForUpdate(matchId).orElseThrow();
                        holderChange.accept(match);
                        matchRepository.flush();
                        locked.countDown();
                        await(release);
                    }));
            awaitSignal(locked, holder, "A가 실제 행 잠금을 획득");
            contender = executor.submit(() -> {
                contenderThread.set(Thread.currentThread());
                return action.call();
            });
            awaitSignal(queryEntered, contender, "B가 매치 조회를 시작");
            // UPDATE에서만 기다려도 Future는 미완료일 수 있으므로 SELECT 자체의 대기도 확인한다.
            assertThat(queryReturned.await(400, TimeUnit.MILLISECONDS))
                    .as("A 커밋 전에는 B의 매치 조회가 반환되지 않아야 함").isFalse();
            assertThat(contender.isDone()).as("A 커밋 전에는 B 서비스가 완료되지 않아야 함").isFalse();
            release.countDown();
            holder.get(5, TimeUnit.SECONDS); // TransactionTemplate 반환 = A 커밋 완료
            T result = contender.get(5, TimeUnit.SECONDS);
            assertThat(queryReturned.getCount()).isZero();
            return result;
        } catch (Exception | AssertionError failure) {
            release.countDown();
            addWorkerFailure(holder, failure);
            addWorkerFailure(contender, failure);
            throw failure;
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
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
        if (worker == null) return;
        try {
            worker.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            failure.addSuppressed(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure.addSuppressed(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제 신호 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Long createMatch(int capacity) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        return transactions.execute(status -> matchService.createMatch(users.get(1L), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("잠금 대상").description("기존 설명")
                .startAt(startAt).endAt(startAt.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build()));
    }

    private void addParticipant(MatchEntity match, Long userId) {
        participantRepository.save(MatchParticipantEntity.builder().match(match).userId(userId)
                .role(MatchParticipantRole.PARTICIPANT).build());
    }

    private MatchModifyRequestDto modification(int capacity) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
        return MatchModifyRequestDto.builder().title("잠금 후 수정").description("수정 설명")
                .startAt(startAt).endAt(startAt.plusHours(3)).maxParticipant(capacity)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();
    }
}
