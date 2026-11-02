package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;
import com.example.sporty.features.exerciseMatching.domain.enums.*;
import com.example.sporty.features.exerciseMatching.repository.*;
import com.example.sporty.features.exerciseMatching.service.*;
import com.example.sporty.support.*;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;

@Import({MatchService.class, FakeClientsConfig.class, UserWithdrawnHandler.class})
class MatchUserGuardMariaDBIntegrationTest extends MariaDBIntegrationTestSupport {
    @Autowired private MatchService service;
    @Autowired private UserWithdrawnHandler handler;
    @Autowired private MatchRepository matches;
    @Autowired private MatchParticipantRepository participants;
    @Autowired private ProcessedEventRepository events;
    @Autowired private MatchUserStateRepository states;
    @Autowired private PlatformTransactionManager manager;
    @MockitoSpyBean private FakeFacilityClient facilities;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean private FakeUserClient users;
    @MockitoSpyBean private MatchUserGuard guard;
    private Map<Long, Long> ids;
    private Long facilityId;

    @BeforeEach
    void setUp() {
        ids = MatchUserFixtures.create(users, 3);
        facilityId = FacilityFixtures.create(facilities, FacilityFixtures.newServiceId()).serviceId();
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(manager, dataSource);
    }

    @Test
    void deploymentSqlMatchesHibernateAndPreservesWithdrawalOnReapply() throws Exception {
        var migrations = List.of(
                new FileSystemResource("docs/sql/2026-11-01-match-user-state.sql"),
                new FileSystemResource("docs/sql/2026-11-02-processed-event.sql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE processed_event");
            statement.execute("DROP TABLE match_user_state");
            for (var migration : migrations) {
                ScriptUtils.executeSqlScript(connection, migration);
            }
        }
        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
        Long userId = ids.get(2L);
        new TransactionTemplate(manager).executeWithoutResult(tx -> guard.markWithdrawn(userId));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String eventId = UUID.randomUUID().toString();
        LocalDateTime processedAt = LocalDateTime.of(2026, 11, 2, 12, 0);
        jdbc.update("INSERT INTO processed_event (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                UserWithdrawnHandler.CONSUMER_NAME, eventId, processedAt);
        var historyBeforeReapply = jdbc.queryForList("SELECT * FROM processed_event");
        try (var connection = dataSource.getConnection()) {
            for (var migration : migrations) {
                ScriptUtils.executeSqlScript(connection, migration);
            }
        }
        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
        assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
        assertThat(events.existsById(eventKey(eventId))).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM processed_event")).isEqualTo(historyBeforeReapply);
        assertThat(jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='processed_event' AND CONSTRAINT_NAME='PRIMARY'
                ORDER BY ORDINAL_POSITION
                """, String.class)).containsExactly("consumer_name", "event_id");
    }

    @ParameterizedTest(name = "registration first, join={0}")
    @ValueSource(booleans = {false, true})
    void withdrawalWaitsForInFlightCreateOrJoinAndCleansItsCommittedData(boolean join) throws Exception {
        Long userId = ids.get(2L);
        Long existingMatch = join ? service.createMatch(ids.get(1L), request()) : null;
        assertThat(states.existsById(userId)).isFalse(); // 최초 행 생성끼리도 경합시킨다.
        CountDownLatch registrationLocked = new CountDownLatch(1);
        CountDownLatch resumeRegistration = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch cleanupLocked = new CountDownLatch(1);
        // MANDATORY 트랜잭션 프록시의 스텁 설정도 트랜잭션 안에서 실행한다.
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            doAnswer(call -> {
                Object result = call.callRealMethod();
                registrationLocked.countDown();
                await(resumeRegistration);
                return result;
            }).when(guard).requireActive(userId);
            doAnswer(call -> {
                cleanupEntered.countDown();
                Object result = call.callRealMethod();
                cleanupLocked.countDown();
                return result;
            }).when(guard).markWithdrawn(userId);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String eventId = UUID.randomUUID().toString();
        try {
            Future<Long> registration = pool.submit(() -> register(join, existingMatch, userId));
            await(registrationLocked);
            users.withdraw(userId);
            Future<?> withdrawal = pool.submit(() -> handler.handle(eventId, userId));
            await(cleanupEntered);
            assertThat(cleanupLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(withdrawal.isDone()).isFalse();
            resumeRegistration.countDown();
            Long matchId = registration.get(15, TimeUnit.SECONDS);
            withdrawal.get(15, TimeUnit.SECONDS);
            assertThat(participants.findMatchIdsByUserIdOrderByMatchIdAsc(userId)).isEmpty();
            assertThat(matches.existsById(matchId)).isEqualTo(join);
            assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
            assertThat(events.existsById(eventKey(eventId))).isTrue();
            handler.handle(eventId, userId);
            assertThat(participants.findMatchIdsByUserIdOrderByMatchIdAsc(userId)).isEmpty();
        } finally {
            resumeRegistration.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "withdrawal first, join={0}")
    @ValueSource(booleans = {false, true})
    void registrationWaitsForWithdrawalCommitAndRejectsEvenStaleActiveUser(boolean join) throws Exception {
        Long userId = ids.get(2L);
        Long existingMatch = join ? service.createMatch(ids.get(1L), request()) : null;
        CountDownLatch marked = new CountDownLatch(1);
        CountDownLatch resumeWithdrawal = new CountDownLatch(1);
        CountDownLatch registrationEntered = new CountDownLatch(1);
        CountDownLatch registrationLocked = new CountDownLatch(1);
        // MANDATORY 트랜잭션 프록시의 스텁 설정도 트랜잭션 안에서 실행한다.
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            doAnswer(call -> {
                Object result = call.callRealMethod();
                marked.countDown();
                await(resumeWithdrawal);
                return result;
            }).when(guard).markWithdrawn(userId);
            doAnswer(call -> {
                registrationEntered.countDown();
                try { return call.callRealMethod(); }
                finally { registrationLocked.countDown(); }
            }).when(guard).requireActive(userId);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String eventId = UUID.randomUUID().toString();
        try {
            Future<?> withdrawal = pool.submit(() -> handler.handle(eventId, userId));
            await(marked);
            // 원격 회원 조회가 아직 ACTIVE를 반환하더라도 로컬 탈퇴 기록이 쓰기를 막아야 한다.
            Future<Long> registration = pool.submit(() -> register(join, existingMatch, userId));
            await(registrationEntered);
            assertThat(registrationLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();
            resumeWithdrawal.countDown();
            withdrawal.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> registration.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(WithdrawnUserFoundException.class);
            verify(users).findUser(userId);
            assertThat(participants.findMatchIdsByUserIdOrderByMatchIdAsc(userId)).isEmpty();
            assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
            assertThat(events.existsById(eventKey(eventId))).isTrue();
        } finally {
            resumeWithdrawal.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "remote user lookup, join={0}")
    @ValueSource(booleans = {false, true})
    void remoteUserLookupDoesNotHoldUserOrMatchLocks(boolean join) throws Exception {
        Long userId = ids.get(2L);
        Long existingMatch = join ? service.createMatch(ids.get(1L), request()) : null;
        CountDownLatch lookupPaused = new CountDownLatch(1);
        CountDownLatch resumeLookup = new CountDownLatch(1);
        doAnswer(call -> {
            // ACTIVE 결과를 미리 확보해 탈퇴 커밋 뒤에도 오래된 응답을 반환한다.
            Object active = call.callRealMethod();
            lookupPaused.countDown();
            await(resumeLookup);
            return active;
        }).when(users).findUser(userId);

        assertRemoteLookupAllowsLockAndWithdrawal(join, existingMatch, userId, lookupPaused, resumeLookup);
        verify(users).findUser(userId);
    }

    @Test
    void remoteFacilityLookupDoesNotHoldUserLock() throws Exception {
        Long userId = ids.get(2L);
        CountDownLatch lookupPaused = new CountDownLatch(1);
        CountDownLatch resumeLookup = new CountDownLatch(1);
        doAnswer(call -> {
            Object facility = call.callRealMethod();
            lookupPaused.countDown();
            await(resumeLookup);
            return facility;
        }).when(facilities).findServices(List.of(facilityId));

        assertRemoteLookupAllowsLockAndWithdrawal(false, null, userId, lookupPaused, resumeLookup);
        verify(users).findUser(userId);
        verify(facilities).findServices(List.of(facilityId));
    }

    private void assertRemoteLookupAllowsLockAndWithdrawal(boolean join, Long existingMatch, Long userId,
            CountDownLatch lookupPaused, CountDownLatch resumeLookup) throws Exception {
        // 같은 사용자 행을 미리 커밋해 실제 존재하는 행의 잠금 획득을 검증한다.
        new TransactionTemplate(manager).executeWithoutResult(tx -> guard.requireActive(userId));
        long matchCount = matches.count();
        long participantCount = participants.count();
        String eventId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> registration = pool.submit(() -> register(join, existingMatch, userId));
            await(lookupPaused);

            Future<?> locksCommitted = pool.submit(() -> new TransactionTemplate(manager)
                    .executeWithoutResult(tx -> {
                        assertThat(states.findByUserIdForUpdate(userId)).isPresent();
                        if (join) {
                            assertThat(matches.findByIdForUpdate(existingMatch)).isPresent();
                        }
                    }));
            // Future 완료는 별도 트랜잭션의 잠금 획득과 커밋을 모두 포함한다.
            locksCommitted.get(15, TimeUnit.SECONDS);
            assertThat(resumeLookup.getCount()).isEqualTo(1L);
            assertThat(registration.isDone()).isFalse();

            users.withdraw(userId);
            Future<?> withdrawal = pool.submit(() -> handler.handle(eventId, userId));
            withdrawal.get(15, TimeUnit.SECONDS);
            assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
            assertThat(events.existsById(eventKey(eventId))).isTrue();
            assertThat(resumeLookup.getCount()).isEqualTo(1L);
            assertThat(registration.isDone()).isFalse();

            resumeLookup.countDown();
            assertThatThrownBy(() -> registration.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(WithdrawnUserFoundException.class);
            assertThat(participants.findMatchIdsByUserIdOrderByMatchIdAsc(userId)).isEmpty();
            assertThat(matches.count()).isEqualTo(matchCount);
            assertThat(participants.count()).isEqualTo(participantCount);
        } finally {
            resumeLookup.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long register(boolean join, Long matchId, Long userId) {
        if (join) {
            service.joinMatch(matchId, userId);
            return matchId;
        }
        return service.createMatch(userId, request());
    }

    private MatchCreateRequestDto request() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return MatchCreateRequestDto.builder().serviceId(facilityId).title("사용자 잠금 경합")
                .startAt(start).endAt(start.plusHours(2)).maxParticipant(3)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).build();
    }

    private static ProcessedEventId eventKey(String eventId) {
        return new ProcessedEventId(UserWithdrawnHandler.CONSUMER_NAME, eventId);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(15, TimeUnit.SECONDS)).as("작업 동기화 신호").isTrue();
    }
}
