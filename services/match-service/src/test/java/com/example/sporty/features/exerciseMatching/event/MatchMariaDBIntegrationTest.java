package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.MariaDBIntegrationTestSupport;
import com.example.sporty.support.MatchUserFixtures;
import com.example.sporty.support.MatchDatabaseCleanup;
import com.fasterxml.jackson.databind.ObjectMapper;

@Import({MatchService.class, FakeClientsConfig.class, UserWithdrawnHandler.class,
        UserWithdrawnListener.class, MatchMariaDBIntegrationTest.JsonConfig.class})
class MatchMariaDBIntegrationTest extends MariaDBIntegrationTestSupport {
    private static final long OWNER = 1L;
    private static final long A = 2L;
    private static final long B = 3L;
    private static final long U = 4L;
    private static final long WAIT_SECONDS = 15;

    @Autowired private UserWithdrawnHandler handler;
    @Autowired private UserWithdrawnListener listener;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FakeUserClient fakeUsers;
    @Autowired private FakeFacilityClient fakeFacilities;
    @MockitoSpyBean private MatchService matchService;
    @MockitoSpyBean private MatchRepository matches;
    @MockitoSpyBean private MatchParticipantRepository participants;
    @MockitoSpyBean private ProcessedEventRepository processedEvents;

    private TransactionTemplate transactions;
    private Map<Long, Long> users;
    private Long serviceId;

    @BeforeEach
    void setUp() {
        // 격리를 지정하지 않은 트랜잭션은 컨테이너의 기본 REPEATABLE READ를 사용한다.
        transactions = new TransactionTemplate(transactionManager);
        users = MatchUserFixtures.create(fakeUsers, 4);
        serviceId = FacilityFixtures.create(fakeFacilities, FacilityFixtures.newServiceId()).serviceId();
    }

    @AfterEach
    void tearDown() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void withdrawalReadsCommittedParticipantsAfterIdCollection() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Long matchId = interleaveWithdrawalAndCapacityChange(eventId, true);

        assertRemainingOwnerAndB(matchId, MatchStatus.RECRUITING);
        assertThat(processedEvents.existsById(id(eventId))).isTrue();
    }

    @Test
    void defaultRepeatableReadReproducesClosedStatusAfterWithdrawal() throws Exception {
        // 2026-10-31 실제 MariaDB에서 재현 확인
        // 격리를 강제로 지정하거나 참가자 집계 결과를 스텁하지 않는다.
        Long matchId = interleaveWithdrawalAndCapacityChange(null, false);

        assertRemainingOwnerAndB(matchId, MatchStatus.CLOSED);
        assertThat(processedEvents.count()).isZero();
    }

    private Long interleaveWithdrawalAndCapacityChange(String eventId, boolean throughHandler) throws Exception {
        Long matchId = createMatch(OWNER, 4);
        joinMatch(matchId, A);
        joinMatch(matchId, B);
        joinMatch(matchId, U);
        assertThat(matches.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
        CountDownLatch idsRead = new CountDownLatch(1);
        CountDownLatch resumeCleanup = new CountDownLatch(1);
        Answer<?> repositoryCalls = originalCalls(participants);
        doAnswer(invocation -> {
            Object ids = repositoryCalls.answer(invocation); // 실제 SELECT를 완료한 뒤 멈춘다.
            assertThat(ids).isEqualTo(java.util.List.of(matchId));
            assertIsolation(throughHandler ? Connection.TRANSACTION_READ_COMMITTED
                    : Connection.TRANSACTION_REPEATABLE_READ);
            idsRead.countDown();
            await(resumeCleanup);
            return ids;
        }).when(participants).findMatchIdsByUserIdOrderByMatchIdAsc(users.get(U));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> cleanup = null;
        try {
            cleanup = executor.submit(() -> transactions.executeWithoutResult(status -> {
                assertIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                if (throughHandler) {
                    // 호출자의 오래된 Match/스냅샷도 새 처리 트랜잭션과 공유하지 않아야 한다.
                    MatchEntity callerMatch = matches.findById(matchId).orElseThrow();
                    handler.handle(eventId, users.get(U));
                    assertThat(callerMatch.getMaxParticipant()).isEqualTo(4);
                } else {
                    // 기본 격리의 새 트랜잭션에서 ID 수집으로 첫 스냅샷을 만든다.
                    matchService.removeMatchesForWithdrawal(users.get(U));
                }
            }));
            awaitSignal(idsRead, cleanup, "탈퇴 처리의 실제 매치 ID 수집 완료");
            transactions.executeWithoutResult(status -> matchService.leaveMatch(matchId, users.get(A)));
            assertThat(participants.countByMatch_Id(matchId)).isEqualTo(3);
            assertThat(matches.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
            transactions.executeWithoutResult(status ->
                    matchService.modifyMatch(matchId, users.get(OWNER), modification(3)));
            assertThat(matches.findById(matchId).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);
            assertThat(matches.findById(matchId).orElseThrow().getMaxParticipant()).isEqualTo(3);
            resumeCleanup.countDown();
            cleanup.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception | AssertionError failure) {
            resumeCleanup.countDown();
            addWorkerFailure(cleanup, failure);
            throw failure;
        } finally {
            resumeCleanup.countDown();
            stop(executor);
        }

        return matchId;
    }

    private void assertRemainingOwnerAndB(Long matchId, MatchStatus expectedStatus) {
        MatchEntity stored = matches.findById(matchId).orElseThrow();
        assertThat(stored.getMaxParticipant()).isEqualTo(3);
        assertThat(stored.getStatus()).isEqualTo(expectedStatus);
        assertThat(participants.countByMatch_Id(matchId)).isEqualTo(2);
        assertThat(participants.findAllByMatch_IdOrderByIdAsc(matchId))
                .extracting(MatchParticipantEntity::getUserId).containsExactly(users.get(OWNER), users.get(B));
    }

    @Test
    void modifyMatchWaitsForRowLockUntilHolderCommits() throws Exception {
        Long matchId = createMatch(OWNER, 3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch queryReturned = new CountDownLatch(1);
        AtomicReference<Thread> contenderThread = new AtomicReference<>();
        Answer<?> repositoryCalls = originalCalls(matches);
        Answer<Object> observeRead = invocation -> {
            boolean contender = Thread.currentThread() == contenderThread.get();
            if (contender) queryEntered.countDown();
            Object result = repositoryCalls.answer(invocation);
            if (contender) queryReturned.countDown();
            return result;
        };
        doAnswer(observeRead).when(matches).findByIdForUpdate(matchId);
        doAnswer(observeRead).when(matches).findById(matchId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = null;
        Future<?> contender = null;
        try {
            holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                MatchEntity match = matches.findByIdForUpdate(matchId).orElseThrow();
                participants.saveAndFlush(MatchParticipantEntity.builder().match(match).userId(users.get(A))
                        .role(MatchParticipantRole.PARTICIPANT).build());
                locked.countDown();
                await(release);
            }));
            awaitSignal(locked, holder, "A 트랜잭션의 실제 행 잠금 획득");
            contender = executor.submit(() -> {
                contenderThread.set(Thread.currentThread());
                return matchService.modifyMatch(matchId, users.get(OWNER), modification(2));
            });
            awaitSignal(queryEntered, contender, "수정 트랜잭션의 매치 조회 진입");
            assertThat(queryReturned.await(400, TimeUnit.MILLISECONDS))
                    .as("잠금 보유자가 커밋하기 전에는 SELECT 자체가 반환되지 않음").isFalse();
            assertThat(contender.isDone()).isFalse();
            release.countDown();
            holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
            contender.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(queryReturned.getCount()).isZero();
        } catch (Exception | AssertionError failure) {
            release.countDown();
            addWorkerFailure(holder, failure);
            addWorkerFailure(contender, failure);
            throw failure;
        } finally {
            release.countDown();
            stop(executor);
        }

        assertThat(participants.countByMatch_Id(matchId)).isEqualTo(2);
        MatchEntity stored = matches.findById(matchId).orElseThrow();
        assertThat(stored.getMaxParticipant()).isEqualTo(2);
        assertThat(stored.getTitle()).isEqualTo("먼저 커밋한 정원 수정");
        assertThat(stored.getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @Test
    void duplicateEventIdKeepsOneReceiptAndDoesNotRepeatCleanup() {
        Long owned = createMatch(U, 3);
        String eventId = UUID.randomUUID().toString();
        listener.onMessage(event(eventId));
        assertThat(matches.existsById(owned)).isFalse();
        LocalDateTime processedAt = processedEvents.findById(id(eventId)).orElseThrow().getProcessedAt();
        assertThatThrownBy(() -> createMatch(U, 3)).isInstanceOf(WithdrawnUserFoundException.class);
        Long addedAfterProcessing = createMatch(OWNER, 3);

        listener.onMessage(event(eventId));

        assertThat(matches.existsById(addedAfterProcessing)).isTrue();
        assertThat(participants.countByMatch_Id(addedAfterProcessing)).isEqualTo(1);
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(processedEvents.findById(id(eventId)).orElseThrow().getProcessedAt()).isEqualTo(processedAt);
        verify(processedEvents, times(1)).insert(eq(UserWithdrawnHandler.CONSUMER_NAME), eq(eventId),
                any(LocalDateTime.class));
        verify(matchService, times(1)).removeMatchesForWithdrawal(users.get(U));
    }

    @Test
    void concurrentDuplicateRollsBackPkConflictAndCleansUpOnce() throws Exception {
        Long owned = createMatch(U, 3);
        String eventId = UUID.randomUUID().toString();
        CountDownLatch bothChecked = new CountDownLatch(2);
        AtomicReference<DataIntegrityViolationException> duplicate = new AtomicReference<>();
        Answer<?> repositoryCalls = originalCalls(processedEvents);
        doAnswer(invocation -> {
            boolean exists = (Boolean) repositoryCalls.answer(invocation);
            if (!exists) {
                bothChecked.countDown();
                await(bothChecked); // 두 트랜잭션 모두 미처리를 확인한 뒤 실제 INSERT로 진행한다.
            }
            return exists;
        }).when(processedEvents).existsById(id(eventId));
        doAnswer(invocation -> {
            try {
                return repositoryCalls.answer(invocation);
            } catch (DataIntegrityViolationException exception) {
                duplicate.set(exception);
                throw exception; // 실제 DB 오류를 처리기에 전달해 실패한 트랜잭션을 롤백한다.
            }
        }).when(processedEvents).insert(eq(UserWithdrawnHandler.CONSUMER_NAME), eq(eventId),
                any(LocalDateTime.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = null;
        Future<?> second = null;
        try {
            // 실제 리스너→처리기를 두 스레드로 호출해 경합시킨다. Kafka 전송은 기존 테스트의 범위다.
            first = executor.submit(() -> listener.onMessage(event(eventId)));
            second = executor.submit(() -> listener.onMessage(event(eventId)));
            first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            second.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception | AssertionError failure) {
            addWorkerFailure(first, failure);
            addWorkerFailure(second, failure);
            throw failure;
        } finally {
            stop(executor);
        }

        assertDuplicateKey(duplicate.get());
        assertThat(matches.existsById(owned)).isFalse();
        assertThat(participants.countByMatch_Id(owned)).isZero();
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(handler.wasProcessed(eventId)).isTrue();
        verify(processedEvents, times(2)).insert(eq(UserWithdrawnHandler.CONSUMER_NAME), eq(eventId),
                any(LocalDateTime.class));
        verify(matchService, times(1)).removeMatchesForWithdrawal(users.get(U));
    }

    @Test
    void reservedMatchTableAndParticipantUniqueConstraintWorkOnMariaDB() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("11.4.8");
        assertThat(jdbc.queryForObject("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'match'
                """, String.class)).isEqualToIgnoringCase("InnoDB");
        assertThat(jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'match_participant'
                  AND INDEX_NAME = 'uk_match_participant_match_user' AND NON_UNIQUE = 0
                ORDER BY SEQ_IN_INDEX
                """, String.class)).containsExactly("match_id", "user_id");
        Long first = createMatch(OWNER, 3);
        Long second = createMatch(OWNER, 3); // 같은 회원도 다른 매치에는 등록 가능
        joinMatch(first, A); // 같은 매치에 다른 회원도 등록 가능

        Throwable failure = catchThrowable(() -> transactions.executeWithoutResult(status ->
                participants.saveAndFlush(MatchParticipantEntity.builder()
                        .match(matches.findById(first).orElseThrow()).userId(users.get(OWNER))
                        .role(MatchParticipantRole.PARTICIPANT).build())));

        assertDuplicateKey(failure);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `match` WHERE id IN (?, ?)",
                Long.class, first, second)).isEqualTo(2L);
        assertThat(participants.countByMatch_Id(first)).isEqualTo(2);
        assertThat(participants.countByMatch_Id(second)).isEqualTo(1);
    }

    @Test
    void processedEventCompositePrimaryKeyWorksOnMariaDB() {
        // 조회는 항상 (consumer_name, event_id) 전체 키를 사용하므로 순서는 동작에 영향 없음.
        // ERD의 순서와 다른 점은 테이블 명세서에 기록
        assertThat(jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'processed_event'
                  AND CONSTRAINT_NAME = 'PRIMARY'
                ORDER BY ORDINAL_POSITION
                """, String.class)).containsExactlyInAnyOrder("consumer_name", "event_id");
        String eventId = UUID.randomUUID().toString();
        String otherEventId = UUID.randomUUID().toString();
        insertReceipt(UserWithdrawnHandler.CONSUMER_NAME, eventId);
        insertReceipt("another-consumer", eventId);
        insertReceipt(UserWithdrawnHandler.CONSUMER_NAME, otherEventId);

        assertDuplicateKey(catchThrowable(() -> insertReceipt(UserWithdrawnHandler.CONSUMER_NAME, eventId)));

        assertThat(processedEvents.count()).isEqualTo(3);
        assertThat(processedEvents.existsById(id(eventId))).isTrue();
        assertThat(processedEvents.existsById(new ProcessedEventId("another-consumer", eventId))).isTrue();
        assertThat(processedEvents.existsById(id(otherEventId))).isTrue();
    }

    private void assertIsolation(int expected) {
        Integer actual = jdbc.execute((ConnectionCallback<Integer>) Connection::getTransactionIsolation);
        assertThat(actual).as("실제 MariaDB 연결의 트랜잭션 격리 수준").isEqualTo(expected);
    }

    private static void assertDuplicateKey(Throwable failure) {
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        Throwable cause = ((DataIntegrityViolationException) failure).getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(1062);
        assertThat(((SQLException) cause).getSQLState()).isEqualTo("23000");
    }

    private void insertReceipt(String consumer, String eventId) {
        transactions.executeWithoutResult(status -> processedEvents.insert(consumer, eventId, LocalDateTime.now()));
    }

    private Long createMatch(long owner, int capacity) {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        return transactions.execute(status -> matchService.createMatch(users.get(owner), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("MariaDB 통합 테스트").description("격리 수준과 잠금 검증")
                .startAt(start).endAt(start.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build()));
    }

    private void joinMatch(Long matchId, long user) {
        transactions.executeWithoutResult(status -> matchService.joinMatch(matchId, users.get(user)));
    }

    private MatchModifyRequestDto modification(int capacity) {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        return MatchModifyRequestDto.builder().title("먼저 커밋한 정원 수정").description("커밋한 설정 보존")
                .startAt(start).endAt(start.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.MIXED).build();
    }

    private String event(String eventId) {
        return """
                {"eventId":"%s","eventType":"UserWithdrawn","payload":{"userId":%d}}
                """.formatted(eventId, users.get(U));
    }

    private static ProcessedEventId id(String eventId) {
        return new ProcessedEventId(UserWithdrawnHandler.CONSUMER_NAME, eventId);
    }

    private static Answer<?> originalCalls(Object repository) {
        // Spring Data 인터페이스에 callRealMethod()를 사용하지 않고 원래 프록시로 위임한다.
        return mockingDetails(repository).getMockCreationSettings().getDefaultAnswer();
    }

    private static void awaitSignal(CountDownLatch signal, Future<?> worker, String description) throws Exception {
        boolean signaled = signal.await(WAIT_SECONDS, TimeUnit.SECONDS);
        if (!signaled && worker.isDone()) {
            try {
                worker.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                throw new AssertionError(description + ": " + exception.getCause(), exception.getCause());
            }
        }
        assertThat(signaled).as(description).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("MariaDB 테스트 동기화 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void addWorkerFailure(Future<?> worker, Throwable failure) {
        if (worker == null) return;
        try {
            worker.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            failure.addSuppressed(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure.addSuppressed(exception);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
