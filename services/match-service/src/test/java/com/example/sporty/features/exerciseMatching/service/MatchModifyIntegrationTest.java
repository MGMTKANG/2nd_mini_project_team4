package com.example.sporty.features.exerciseMatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchCapacityBelowParticipantsException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchDetailResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({MatchService.class, FakeClientsConfig.class})
// 서비스 호출의 실제 커밋/롤백 이후 별도 조회로 DB 상태를 확인한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchModifyIntegrationTest {

    @Autowired private MatchService matchService;
    @Autowired private MatchRepository matchRepository;
    @Autowired private MatchParticipantRepository matchParticipantRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FakeUserClient fakeUserClient;
    @Autowired private FakeFacilityClient fakeFacilityClient;

    private Map<Long, Long> users;
    private Long serviceId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            matchParticipantRepository.deleteAllInBatch();
            matchRepository.deleteAllInBatch();
        });
        users = MatchUserFixtures.create(fakeUserClient, 3);
        serviceId = FacilityFixtures.create(fakeFacilityClient, FacilityFixtures.newServiceId()).serviceId();
    }

    @Test
    @DisplayName("OWNER 포함 현재 인원 3명보다 작은 정원 2명 요청은 모든 매치 필드를 보존한다")
    void capacityBelowParticipantsPreservesStoredMatch() {
        Long matchId = createMatch(4, 3);
        MatchEntity before = matchRepository.findById(matchId).orElseThrow();
        List<Long> participantIds = matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId)
                .stream().map(MatchParticipantEntity::getId).toList();

        assertThatThrownBy(() -> matchService.modifyMatch(matchId, users.get(1L), modification(2)))
                .isInstanceOf(MatchCapacityBelowParticipantsException.class)
                .hasMessage("모집 정원은 현재 참가 인원 이상이어야 합니다.");

        MatchEntity after = matchRepository.findById(matchId).orElseThrow();
        assertThat(after).usingRecursiveComparison().isEqualTo(before);
        assertState(matchId, 3, 4, MatchStatus.RECRUITING);
        assertThat(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .extracting(MatchParticipantEntity::getId).containsExactlyElementsOf(participantIds);
    }

    @Test
    @DisplayName("모집 중인 매치의 정원을 현재 인원 2명으로 줄이면 CLOSED가 저장된다")
    void reducingCapacityToParticipantCountClosesRecruitment() {
        Long matchId = createMatch(3, 2);
        assertState(matchId, 2, 3, MatchStatus.RECRUITING);

        MatchDetailResponseDto response = matchService.modifyMatch(matchId, users.get(1L), modification(2));

        assertThat(response.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(response.getMaxParticipant()).isEqualTo(2);
        assertThat(response.getCurrentParticipantCount()).isEqualTo(2);
        assertState(matchId, 2, 2, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("정원 2명이 찬 CLOSED 매치의 정원을 3명으로 늘리면 모집을 재개한다")
    void increasingFullCapacityReopensRecruitment() {
        Long matchId = createMatch(2, 2);
        assertState(matchId, 2, 2, MatchStatus.CLOSED);

        MatchDetailResponseDto response = matchService.modifyMatch(matchId, users.get(1L), modification(3));

        assertThat(response.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(response.getMaxParticipant()).isEqualTo(3);
        assertThat(response.getCurrentParticipantCount()).isEqualTo(2);
        assertState(matchId, 2, 3, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("시작 시각이 지난 CLOSED 매치는 정원을 늘려도 모집을 재개하지 않는다")
    void increasingCapacityAfterStartKeepsRecruitmentClosed() {
        Long matchId = createMatch(2, 2);
        assertState(matchId, 2, 2, MatchStatus.CLOSED);
        LocalDateTime startedAt = LocalDateTime.now().minusHours(1).withNano(0);
        MatchModifyRequestDto request = MatchModifyRequestDto.builder().title("수정 제목").description("수정 설명")
                .startAt(startedAt).endAt(startedAt.plusHours(3)).maxParticipant(3)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();

        MatchDetailResponseDto response = matchService.modifyMatch(matchId, users.get(1L), request);

        assertThat(response.getStatus()).isEqualTo(MatchStatus.CLOSED);
        assertThat(response.getMaxParticipant()).isEqualTo(3);
        assertState(matchId, 2, 3, MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("모집 중인 매치의 정원만 늘리면 RECRUITING을 유지한다")
    void increasingRecruitingCapacityKeepsRecruitmentOpen() {
        Long matchId = createMatch(3, 2);
        MatchEntity before = matchRepository.findById(matchId).orElseThrow();
        MatchModifyRequestDto request = MatchModifyRequestDto.builder()
                .title(before.getTitle()).description(before.getDescription())
                .startAt(before.getStartAt()).endAt(before.getEndAt()).maxParticipant(4)
                .skillLevel(before.getSkillLevel()).genderGroup(before.getGenderGroup()).build();

        MatchDetailResponseDto response = matchService.modifyMatch(matchId, users.get(1L), request);

        assertThat(response.getStatus()).isEqualTo(MatchStatus.RECRUITING);
        MatchEntity after = matchRepository.findById(matchId).orElseThrow();
        assertThat(after).usingRecursiveComparison().ignoringFields("maxParticipant", "updatedAt")
                .isEqualTo(before);
        assertState(matchId, 2, 4, MatchStatus.RECRUITING);
    }

    @Test
    @DisplayName("마지막 자리 참가와 정원 축소가 동시에 실행돼도 정원과 상태가 일치한다")
    void concurrentJoinAndCapacityReductionPreserveCapacityAndStatus() throws Exception {
        Long matchId = createMatch(3, 2);
        MatchModifyRequestDto request = modification(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Object joinResult;
        Object modifyResult;
        try {
            Future<Object> join = executor.submit(() -> afterSignal(ready, start,
                    () -> matchService.joinMatch(matchId, users.get(3L))));
            Future<Object> modify = executor.submit(() -> afterSignal(ready, start,
                    () -> matchService.modifyMatch(matchId, users.get(1L), request)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            joinResult = join.get(10, TimeUnit.SECONDS);
            modifyResult = modify.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        // 두 순서 중 하나만 허용한다. 잠금·DB 예외는 정상 결과로 취급하지 않는다.
        if (joinResult instanceof MatchParticipantResponseDto) {
            assertThat(modifyResult).isInstanceOf(MatchCapacityBelowParticipantsException.class);
            assertState(matchId, 3, 3, MatchStatus.CLOSED);
            assertThat(matchParticipantRepository.existsByMatch_IdAndUserId(matchId, users.get(3L))).isTrue();
        } else {
            assertThat(joinResult).isInstanceOf(MatchRecruitmentClosedException.class);
            assertThat(modifyResult).isInstanceOf(MatchDetailResponseDto.class);
            assertState(matchId, 2, 2, MatchStatus.CLOSED);
            assertThat(matchParticipantRepository.existsByMatch_IdAndUserId(matchId, users.get(3L))).isFalse();
        }

        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        long participantCount = matchParticipantRepository.countByMatch_Id(matchId);
        assertThat(participantCount).isLessThanOrEqualTo(stored.getMaxParticipant().longValue());
        assertThat(stored.getStatus()).isEqualTo(participantCount == stored.getMaxParticipant()
                ? MatchStatus.CLOSED : MatchStatus.RECRUITING);
    }

    private Object afterSignal(CountDownLatch ready, CountDownLatch start, Callable<Object> action)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호 대기 시간 초과");
        }
        try {
            return action.call();
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Long createMatch(int capacity, int participantCount) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        Long matchId = matchService.createMatch(users.get(1L), MatchCreateRequestDto.builder()
                .serviceId(serviceId).title("기존 제목").description("기존 설명")
                .startAt(startAt).endAt(startAt.plusHours(2)).maxParticipant(capacity)
                .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build());
        for (long label = 2; label <= participantCount; label++) {
            matchService.joinMatch(matchId, users.get(label));
        }
        return matchId;
    }

    private MatchModifyRequestDto modification(int capacity) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
        return MatchModifyRequestDto.builder().title("수정 제목").description("수정 설명")
                .startAt(startAt).endAt(startAt.plusHours(3)).maxParticipant(capacity)
                .skillLevel(SkillLevel.ADVANCED).genderGroup(GenderGroup.FEMALE).build();
    }

    private void assertState(Long matchId, long count, int capacity, MatchStatus status) {
        MatchEntity stored = matchRepository.findById(matchId).orElseThrow();
        assertThat(matchParticipantRepository.countByMatch_Id(matchId)).isEqualTo(count);
        assertThat(stored.getMaxParticipant()).isEqualTo(capacity);
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId))
                .filteredOn(participant -> participant.getRole() == MatchParticipantRole.OWNER).hasSize(1);
    }
}
