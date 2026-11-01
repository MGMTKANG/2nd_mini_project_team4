package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
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
    @Autowired private FakeFacilityClient facilities;
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
        var migration = new FileSystemResource("docs/sql/2026-11-01-match-user-state.sql");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE match_user_state");
            ScriptUtils.executeSqlScript(connection, migration);
        }
        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
        Long userId = ids.get(2L);
        new TransactionTemplate(manager).executeWithoutResult(tx -> guard.markWithdrawn(userId));
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, migration);
        }
        assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
    }

    @ParameterizedTest(name = "registration first, join={0}")
    @ValueSource(booleans = {false, true})
    void withdrawalWaitsForInFlightCreateOrJoinAndCleansItsCommittedData(boolean join) throws Exception {
        Long userId = ids.get(2L);
        Long existingMatch = join ? service.createMatch(ids.get(1L), request()) : null;
        assertThat(states.existsById(userId)).isFalse(); // 최초 행 생성끼리도 경합시킨다.
        CountDownLatch activeChecked = new CountDownLatch(1);
        CountDownLatch resumeRegistration = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch cleanupLocked = new CountDownLatch(1);
        doAnswer(call -> {
            Object active = call.callRealMethod();
            activeChecked.countDown();
            await(resumeRegistration);
            return active;
        }).when(users).findUser(userId);
        // MANDATORY 트랜잭션 프록시의 스텁 설정도 트랜잭션 안에서 실행한다.
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
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
            await(activeChecked);
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
            verify(users, never()).findUser(userId);
            assertThat(participants.findMatchIdsByUserIdOrderByMatchIdAsc(userId)).isEmpty();
            assertThat(states.findById(userId).orElseThrow().isWithdrawn()).isTrue();
            assertThat(events.existsById(eventKey(eventId))).isTrue();
        } finally {
            resumeWithdrawal.countDown();
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
