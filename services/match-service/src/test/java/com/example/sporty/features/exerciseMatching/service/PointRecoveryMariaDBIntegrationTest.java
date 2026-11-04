package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.PointDebitEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.repository.PointDebitRepository;
import com.example.sporty.features.exerciseMatching.repository.PointRefundRepository;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakePointClient;
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.MariaDBIntegrationTestSupport;
import com.example.sporty.support.MatchDatabaseCleanup;
import com.example.sporty.support.MatchUserFixtures;

/** Actual MariaDB commits/rollbacks and locks; remote point effects use an idempotent fake. */
@Import({MatchService.class, FakeClientsConfig.class})
class PointRecoveryMariaDBIntegrationTest extends MariaDBIntegrationTestSupport {
    private static final long INITIAL_BALANCE = 10000L;
    private static final long CONTRIBUTION = 1000L;

    @Autowired private MatchService matchService;
    @Autowired private PointDebitJournal journal;
    @Autowired private PointDebitRecovery recovery;
    @Autowired private PointRefundProcessor refunds;
    @Autowired private PointDebitRepository debitRepository;
    @Autowired private PointRefundRepository refundRepository;
    @Autowired private MatchRepository matches;
    @Autowired private MatchParticipantRepository participants;
    @Autowired private FakePointClient points;
    @Autowired private FakeUserClient users;
    @Autowired private FakeFacilityClient facilities;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    private Long owner;
    private Long member;
    private Long facilityId;

    @BeforeEach
    void setUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
        Map<Long, Long> fixture = MatchUserFixtures.create(users, 2);
        owner = fixture.get(1L);
        member = fixture.get(2L);
        points.setBalance(owner, INITIAL_BALANCE);
        points.setBalance(member, INITIAL_BALANCE);
        facilityId = FacilityFixtures.create(facilities, FacilityFixtures.newServiceId()).id();
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void databaseFailureAfterDebitCompensatesWithoutLeavingMatchOrParticipant() {
        // Direct service invocation bypasses HTTP length validation and fails the real VARCHAR(50) insert.
        assertThatThrownBy(() -> matchService.createMatch(owner, request("x".repeat(51))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(matches.count()).isZero();
        assertThat(participants.count()).isZero();
        assertThat(onlyDebit().getStatus()).isEqualTo("CANCELLED");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(owner)).isOne();
    }

    @Test
    void lostDebitResponseRollsBackMatchAndCompensatesCommittedRemoteDebit() {
        points.loseNextDebitResponseFor(owner);

        assertThatThrownBy(() -> matchService.createMatch(owner, request("차감 응답 유실")))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        assertThat(matches.count()).isZero();
        assertThat(participants.count()).isZero();
        assertThat(onlyDebit().getStatus()).isEqualTo("CANCELLED");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(owner)).isOne();
    }

    @Test
    void lostCompensationResponseLeavesPendingJournalAndRetryDoesNotCreditAgain() {
        points.loseNextDebitResponseFor(owner);
        points.loseNextRefundResponseFor(owner);

        assertThatThrownBy(() -> matchService.createMatch(owner, request("보상 응답 유실")))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        PointDebitEntity pending = onlyDebit();
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(owner)).isOne();
        recovery.cancelPending(pending.getOperationId());
        recovery.cancelPending(pending.getOperationId());
        assertThat(onlyDebit().getStatus()).isEqualTo("CANCELLED");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(owner)).isOne();
        assertThat(matches.count()).isZero();
    }

    @Test
    void persistedIntentRecoversAfterSimulatedProcessExitBeforeMatchCommit() {
        // Reconstruct the durable state left after remote commit without invoking an in-process rollback hook.
        String id = journal.prepare(owner, CONTRIBUTION);
        points.deduct(owner, CONTRIBUTION, id);
        assertThat(onlyDebit().getStatus()).isEqualTo("PENDING");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE - CONTRIBUTION);

        recovery.cancelPending(id);

        assertThat(onlyDebit().getStatus()).isEqualTo("CANCELLED");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(owner)).isOne();
        assertThat(matches.count()).isZero();
    }

    @Test
    void appliedDebitIsNeverCompensatedByRecovery() {
        Long matchId = matchService.createMatch(owner, request("커밋된 매치"));
        String id = participantOperationId(matchId, owner);
        assertThat(onlyDebit().getStatus()).isEqualTo("APPLIED");

        recovery.cancelPending(id);

        assertThat(onlyDebit().getStatus()).isEqualTo("APPLIED");
        assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE - CONTRIBUTION);
        assertThat(points.getRefundCallCount(owner)).isZero();
        assertThat(participants.findByMatch_IdAndUserId(matchId, owner)).isPresent();
    }

    @Test
    void rolledBackLeaveDoesNotSendRefundOrPersistOutbox() {
        Long matchId = matchService.createMatch(owner, request("참가 취소 롤백"));
        matchService.joinMatch(matchId, member);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchService.leaveMatch(matchId, member);
            throw new IllegalStateException("force rollback after local participant deletion");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(refundRepository.count()).isZero();
        assertThat(points.getRefundCallCount(member)).isZero();
        assertThat(points.getBalance(member)).isEqualTo(INITIAL_BALANCE - CONTRIBUTION);
        assertThat(participants.findByMatch_IdAndUserId(matchId, member).orElseThrow().getContributionPoints())
                .isEqualTo(CONTRIBUTION);
    }

    @Test
    void leaveCommitsDespiteLostRefundResponseAndOutboxRetryDoesNotCreditTwice() {
        Long matchId = matchService.createMatch(owner, request("탈퇴 환불 유실"));
        matchService.joinMatch(matchId, member);
        String id = participantOperationId(matchId, member);
        points.loseNextRefundResponseFor(member);

        matchService.leaveMatch(matchId, member);

        assertThat(participants.findByMatch_IdAndUserId(matchId, member)).isEmpty();
        assertThat(matches.existsById(matchId)).isTrue();
        assertPendingRefundAlreadyCredited(id, member);
        refunds.process(id);
        refunds.process(id);
        assertCompletedRefundCreditedOnce(id, member);
    }

    @Test
    void deleteCommitsDespiteLostRefundResponseAndOutboxSurvivesDeletedParticipant() {
        Long matchId = matchService.createMatch(owner, request("삭제 환불 유실"));
        String id = participantOperationId(matchId, owner);
        points.loseNextRefundResponseFor(owner);

        matchService.deleteMatch(matchId, owner);

        assertThat(matches.existsById(matchId)).isFalse();
        assertThat(participants.countByMatch_Id(matchId)).isZero();
        assertPendingRefundAlreadyCredited(id, owner);
        refunds.process(id);
        refunds.process(id);
        assertCompletedRefundCreditedOnce(id, owner);
    }

    @Test
    void recoveryWaitsForLiveMatchTransactionThenSkipsItsCommittedDebit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch localWritesReady = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        AtomicReference<String> operation = new AtomicReference<>();
        try {
            var writer = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                Long matchId = matchService.createMatch(owner, request("진행 중인 매치 트랜잭션"));
                operation.set(participantOperationId(matchId, owner));
                localWritesReady.countDown();
                await(allowCommit);
                return matchId;
            }));
            assertThat(localWritesReady.await(10, TimeUnit.SECONDS)).isTrue();
            var recovering = pool.submit(() -> {
                recoveryStarted.countDown();
                recovery.cancelPending(operation.get());
            });
            assertThat(recoveryStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> recovering.get(400, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThat(points.getRefundCallCount(owner)).isZero();
            allowCommit.countDown();
            Long matchId = writer.get(10, TimeUnit.SECONDS);
            recovering.get(10, TimeUnit.SECONDS);

            assertThat(debitRepository.findById(operation.get()).orElseThrow().getStatus()).isEqualTo("APPLIED");
            assertThat(points.getBalance(owner)).isEqualTo(INITIAL_BALANCE - CONTRIBUTION);
            assertThat(points.getRefundCallCount(owner)).isZero();
            assertThat(participants.findByMatch_IdAndUserId(matchId, owner)).isPresent();
        } finally {
            allowCommit.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private PointDebitEntity onlyDebit() {
        var debits = debitRepository.findAll();
        assertThat(debits).hasSize(1);
        return debits.get(0);
    }

    private String participantOperationId(Long matchId, Long userId) {
        String id = participants.findByMatch_IdAndUserId(matchId, userId).orElseThrow().getPointOperationId();
        assertThat(id).isNotBlank();
        return id;
    }

    private void assertPendingRefundAlreadyCredited(String id, Long userId) {
        assertThat(refundRepository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(points.getBalance(userId)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(userId)).isOne();
    }

    private void assertCompletedRefundCreditedOnce(String id, Long userId) {
        assertThat(refundRepository.findById(id).orElseThrow().getStatus()).isEqualTo("DONE");
        assertThat(points.getBalance(userId)).isEqualTo(INITIAL_BALANCE);
        assertThat(points.getRefundCallCount(userId)).isOne();
    }

    private MatchCreateRequestDto request(String title) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        return MatchCreateRequestDto.builder().serviceId(facilityId).title(title).description("포인트 복구 검증")
                .startAt(start).endAt(start.plusHours(1)).maxParticipant(4).targetPoints(4000L)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL).genderGroup(GenderGroup.MIXED).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("transaction latch timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
