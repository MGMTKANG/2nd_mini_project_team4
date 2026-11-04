package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import javax.sql.DataSource;

import com.example.sporty.features.commons.exception.exerciseMatching.FundingNotFailedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.repository.PointRefundRepository;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakePointClient;
import com.example.sporty.support.FakeUserClient;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.support.MatchDatabaseCleanup;
import com.example.sporty.support.MatchUserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({MatchService.class, FakeClientsConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FundingFailureIntegrationTest {
    @Autowired private MatchService matchService;
    @Autowired private MatchRepository matchRepository;
    @Autowired private MatchParticipantRepository participantRepository;
    @Autowired private PointRefundRepository refunds;
    @Autowired private PointRefundProcessor refundProcessor;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private FakeUserClient users;
    @Autowired private FakeFacilityClient facilities;
    @Autowired private FakePointClient points;
    private Map<Long, Long> userIds;
    private Long serviceId;

    @BeforeEach
    void setUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
        userIds = MatchUserFixtures.create(users, 4);
        serviceId = FacilityFixtures.create(facilities, FacilityFixtures.newServiceId()).id();
        userIds.values().forEach(id -> points.setBalance(id, 10000L));
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void refundedMatchCannotReopenThroughLeaveOrFutureScheduleEdit() {
        Long matchId = createFundedMatch(4);
        matchService.joinMatch(matchId, userIds.get(2L));
        matchService.failFunding(matchId);
        assertFailed(matchId);
        assertThat(matchService.getMatchDetail(matchId, userIds.get(1L)).getFundedPoints()).isZero();

        matchService.leaveMatch(matchId, userIds.get(2L));
        assertFailed(matchId);
        matchService.modifyMatch(matchId, userIds.get(1L), futureEdit(4));
        assertFailed(matchId);
        assertThatThrownBy(() -> matchService.joinMatch(matchId, userIds.get(3L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);
        assertThat(points.getBalance(userIds.get(3L))).isEqualTo(10000L);
        assertThat(participantRepository.countByMatch_Id(matchId)).isOne();
    }

    @Test
    void partialRefundFailureKeepsTerminalStateAndCanRetryRemainingRefunds() {
        Long matchId = createFundedMatch(4);
        matchService.joinMatch(matchId, userIds.get(2L));
        points.failNextRefundFor(userIds.get(2L));

        // 종료·환불 의뢰가 원자적으로 커밋되면 일시적인 원격 환불 실패를 API 실패로 되돌리지 않는다.
        matchService.failFunding(matchId);
        assertFailed(matchId);
        assertThat(points.getBalance(userIds.get(1L))).isEqualTo(10000L);
        assertThat(points.getBalance(userIds.get(2L))).isEqualTo(7500L);
        assertThat(matchService.getMatchDetail(matchId, userIds.get(1L)).getFundedPoints()).isZero();
        assertThat(participantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .allSatisfy(participant -> assertThat(participant.getContributionPoints()).isNull());
        var pending = refunds.findTop50ByStatusAndOperationIdGreaterThanOrderByOperationIdAsc("PENDING", "");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getUserId()).isEqualTo(userIds.get(2L));
        assertThatThrownBy(() -> matchService.joinMatch(matchId, userIds.get(3L)))
                .isInstanceOf(MatchRecruitmentClosedException.class);
        matchService.modifyMatch(matchId, userIds.get(1L), futureEdit(4));
        assertFailed(matchId);

        // 반복 실패 트리거도 새 환불 의뢰를 만들지 않는다. 남은 의뢰만 재전송한다.
        matchService.failFunding(matchId);
        assertThat(refunds.findTop50ByStatusAndOperationIdGreaterThanOrderByOperationIdAsc("PENDING", "")).hasSize(1);
        refundProcessor.process(pending.get(0).getOperationId());
        refundProcessor.process(pending.get(0).getOperationId());
        assertThat(refunds.findTop50ByStatusAndOperationIdGreaterThanOrderByOperationIdAsc("PENDING", "")).isEmpty();
        assertFailed(matchId);
        assertThat(points.getBalance(userIds.get(1L))).isEqualTo(10000L);
        assertThat(points.getBalance(userIds.get(2L))).isEqualTo(10000L);
        assertThat(points.getRefundCallCount(userIds.get(1L))).isOne();
        assertThat(points.getRefundCallCount(userIds.get(2L))).isOne();
        assertThat(matchService.getMatchDetail(matchId, userIds.get(1L)).getFundedPoints()).isZero();
    }

    @Test
    void successfulFundingCannotBeMarkedFailed() {
        Long matchId = createFundedMatch(2);
        matchService.joinMatch(matchId, userIds.get(2L));
        assertThatThrownBy(() -> matchService.failFunding(matchId))
                .isInstanceOf(FundingNotFailedException.class);
        MatchEntity saved = matchRepository.findById(matchId).orElseThrow();
        assertThat(saved.isFundingFailed()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(points.getBalance(userIds.get(1L))).isEqualTo(5000L);
        assertThat(points.getBalance(userIds.get(2L))).isEqualTo(5000L);
    }

    private void assertFailed(Long matchId) {
        MatchEntity saved = matchRepository.findById(matchId).orElseThrow();
        assertThat(saved.isFundingFailed()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(matchService.getMatchDetail(matchId, userIds.get(1L)).isFundingFailed()).isTrue();
    }

    private Long createFundedMatch(int capacity) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        return matchService.createMatch(userIds.get(1L), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("펀딩 종료 회귀 테스트").description("테스트")
                .startAt(start).endAt(start.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).targetPoints(10000L).build());
    }

    private MatchModifyRequestDto futureEdit(int capacity) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        return MatchModifyRequestDto.builder().title("일정 변경 후에도 종료 유지").description("테스트")
                .startAt(start).endAt(start.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).genderGroup(GenderGroup.MIXED).build();
    }
}
