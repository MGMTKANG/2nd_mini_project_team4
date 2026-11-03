package com.example.sporty.features.exerciseMatching.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.exerciseMatching.MatchDeleteForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchModifyForbiddenException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchOwnerCannotLeaveException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchParticipantNotFoundException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyJoinedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchAlreadyStartedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchCapacityBelowParticipantsException;
import com.example.sporty.features.commons.exception.exerciseMatching.FundedMatchCapacityImmutableException;
import com.example.sporty.features.commons.exception.exerciseMatching.FundingNotFailedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchNotFundedException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchFullException;
import com.example.sporty.features.commons.exception.exerciseMatching.MatchRecruitmentClosedException;
import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.UserClient.ProfileSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchDetailResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchModifyRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchParticipantSummaryDto;
import com.example.sporty.features.commons.exception.matches.MatchUserNotFoundException;
import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import com.example.sporty.features.commons.exception.matches.ServiceNotFoundException;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchCreateRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MyMatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {
    // API의 오프셋 없는 경기 시각은 서울 지역 시각이다. 호스트/JVM 기본 시간대에 의존하지 않는다.
    private static final ZoneId MATCH_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final UserClient userClient;
    private final FacilityClient facilityClient;
    private final PointClient pointClient;
    private final MatchUserGuard userGuard;
    private final ContributionRefundService contributionRefundService;

    // [EM-08] 내 매치 목록 조회
    public List<MyMatchResponseDto> getMyMatches(Long userId) {
        // 1. 로그인 회원 확인
        UserSummary user = userClient.findUser(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!user.isActive()) {
            throw new WithdrawnUserFoundException();
        }

        // 2. 본인이 생성하거나 참여한 매치 조회
        List<MatchParticipantEntity> participants = matchParticipantRepository
                .findAllByUserIdOrderByMatch_StartAtDescMatch_IdDesc(userId);
        if (participants.isEmpty()) {
            return List.of();
        }

        // 3. 기존 전체 목록 조회와 동일하게 참가 인원을 한 번에 집계
        List<Long> matchIds = participants.stream().map(participant -> participant.getMatch().getId()).toList();
        Map<Long, Long> countsByMatchId = matchRepository.countByMatchIds(matchIds).stream()
                .collect(Collectors.toMap(row -> row.getMatchId(), row -> row.getParticipantCount()));
        Map<String, FacilityServiceSummary> facilitiesById = findFacilities(participants.stream()
                .map(participant -> participant.getMatch().getServiceId()).toList());

        // 4. 생성자/일반 참가자 역할을 포함한 목록 반환
        return participants.stream()
                .map(participant -> MyMatchResponseDto.toResponseDto(participant,
                        countsByMatchId.getOrDefault(participant.getMatch().getId(), 0L),
                        facilitiesById.get(participant.getMatch().getServiceId())))
                .toList();
    }

    /**
     * EM-07: 인증된 일반 참가자의 참가 정보만 삭제한다. 경기 시작 후에도 탈퇴 가능하다.
     * OWNER는 탈퇴 대신 매치 삭제 기능을 사용해야 한다.
     * 시작 전에는 빈자리가 생기면 모집을 재개하고, 시작 이후에는 모집 상태를 유지한다.
     */
    @Transactional
    public void leaveMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository
                .findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        MatchParticipantEntity participant = matchParticipantRepository
                .findByMatch_IdAndUserId(matchId, userId)
                .orElseThrow(MatchParticipantNotFoundException::new);

        // 매치 생성자는 탈퇴할 수 없다.
        if (participant.getRole() == MatchParticipantRole.OWNER) {
            throw new MatchOwnerCannotLeaveException();
        }

        removeParticipantAndReopenRecruitment(match, participant);
    }

    private void removeParticipantAndReopenRecruitment(MatchEntity match, MatchParticipantEntity participant) {
        refundContribution(participant);
        matchParticipantRepository.delete(participant);
        // 같은 트랜잭션의 삭제를 반영한 인원으로 확인하며, 참여 API와 매치 잠금을 공유한다.
        // CLOSED 상태에서 탈퇴했을 때, 매칭 시작 전 + 인원 제한 수 보다 적으면 다시 RECRUITING 상태로 변경
        if (match.getStatus() == MatchStatus.CLOSED
                && match.getStartAt().isAfter(LocalDateTime.now(MATCH_TIME_ZONE))
                && matchParticipantRepository.countByMatch_Id(match.getId()) < match.getMaxParticipant()) {
            match.reopenRecruitment();
        }
    }

    /**
     * [USR-04] 회원탈퇴: 생성한 매치는 전체 삭제, 다른 매치에서는 본인 참가 기록만 삭제한다.
     * 운영 호출은 UserWithdrawnHandler의 새 READ_COMMITTED 트랜잭션 경계 안에서만 한다.
     */
    @Transactional
    public void removeMatchesForWithdrawal(Long userId) {
        List<Long> matchIds = matchParticipantRepository
                .findMatchIdsByUserIdOrderByMatchIdAsc(userId);

        // ID만 수집한 뒤 오름차순으로 잠그고, 잠금 후 처음 읽은 엔티티로만 판단·변경한다.
        for (Long matchId : matchIds) {
            MatchEntity match = matchRepository.findByIdForUpdate(matchId).orElse(null);
            if (match == null) continue;
            var participation = matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId);
            if (participation.isEmpty()) continue;
            if (participation.get().getRole() == MatchParticipantRole.OWNER) {
                refundAllContributions(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId));
                matchParticipantRepository.deleteAllByMatch_Id(matchId);
                matchRepository.delete(match);
            } else {
                removeParticipantAndReopenRecruitment(match, participation.get());
            }
        }
    }

    // EM-06: 참가자 저장과 정원 도달 시 모집 마감을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public MatchParticipantResponseDto joinMatch(Long matchId, Long userId) {
        UserSummary user = userClient.findUser(userId)
                .orElseThrow(MatchUserNotFoundException::new);

        if (!user.isActive()) {
            throw new WithdrawnUserFoundException();
        }

        // 원격 확인 중에는 행을 잠그지 않고, 쓰기 직전 로컬 탈퇴 상태를 잠금으로 확정한다.
        userGuard.requireActive(userId);
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        // OWNER도 이미 등록된 참가자이므로 역할과 관계없이 중복 참여를 막는다.
        if (matchParticipantRepository.existsByMatch_IdAndUserId(matchId, userId)) {
            throw new MatchAlreadyJoinedException();
        }

        if (match.getStatus() != MatchStatus.RECRUITING) {
            throw new MatchRecruitmentClosedException();
        }

        // 잠금을 기다리는 동안 시작 시각이 지났을 수도 있으므로 조회 후 현재 시각을 확인한다.
        if (!match.getStartAt().isAfter(LocalDateTime.now(MATCH_TIME_ZONE))) {
            throw new MatchAlreadyStartedException();
        }

        long participantCount = matchParticipantRepository.countByMatch_Id(matchId);

        if (participantCount >= match.getMaxParticipant()) {
            throw new MatchFullException();
        }

        // 크라우드펀딩 매치만 참가비를 차감한다 (SAGA 보상 트랜잭션의 시작점).
        // 참가자 행을 만들기 전에 차감해서, 포인트 부족 시 롤백할 참가 기록이 없게 한다.
        Long contributionPoints = null;
        if (match.getTargetPoints() != null) {
            contributionPoints = (long) Math.ceil(match.getTargetPoints() / (double) match.getMaxParticipant());
            pointClient.deduct(userId, contributionPoints);
        }

        MatchParticipantEntity participant = matchParticipantRepository.save(
                MatchParticipantEntity.builder()
                        .match(match)
                        .userId(userId)
                        .role(MatchParticipantRole.PARTICIPANT)
                        .contributionPoints(contributionPoints)
                        .build());

        if (participantCount + 1 == match.getMaxParticipant()) {
            match.closeRecruitment();
        }

        return MatchParticipantResponseDto.builder()
                .matchParticipantId(participant.getId())
                .matchId(matchId)
                .userId(participant.getUserId())
                .role(participant.getRole())
                .build();
    }


    /**
     * SAGA 데모용 수동 트리거: 정원 미달로 펀딩이 실패했다고 보고 매치를 마감하고,
     * 참가비를 낸 참가자 전원에게 보상 트랜잭션(환불)을 수행한다.
     * 실제 마감 시각 판정 로직(배치/크론)은 데모 범위에서 생략한다.
     */
    @Transactional
    public void failFunding(Long matchId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        // 크라우드펀딩 매치가 아니면 "펀딩 실패"라는 개념 자체가 성립하지 않는다.
        if (match.getTargetPoints() == null) {
            throw new MatchNotFundedException();
        }

        // 정원이 이미 찼다면(펀딩 성공) 환불 대상이 아니다. 이미 CLOSED여도 정원 미달 사유일 수 있으니 인원으로만 판단한다.
        long participantCount = matchParticipantRepository.countByMatch_Id(matchId);
        if (participantCount >= match.getMaxParticipant()) {
            throw new FundingNotFailedException();
        }

        refundAllContributions(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId));

        match.closeRecruitment();
    }

    // 참가 취소·매치 삭제·회원 탈퇴로 참가 기록이 사라지기 전에, 차감된 참가비가 있으면 환불한다.
    // 호출자의 트랜잭션과 분리된(REQUIRES_NEW) 트랜잭션에서 커밋한다 - ContributionRefundService 참고.
    private void refundContribution(MatchParticipantEntity participant) {
        contributionRefundService.refundContribution(participant);
    }

    // 여러 명을 순서대로 환불하다가 한 명이 실패해도, 각자 독립된 트랜잭션이라 이미
    // point-service에 커밋된 앞선 환불은 이 메서드가 속한 트랜잭션 롤백에 영향받지 않는다.
    private void refundAllContributions(List<MatchParticipantEntity> participants) {
        for (MatchParticipantEntity participant : participants) {
            refundContribution(participant);
        }
    }

    // EM-05: 참가 기록과 매치를 한 트랜잭션에서 삭제한다.
    @Transactional
    public void deleteMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId)
                .filter(participant -> participant.getRole() == MatchParticipantRole.OWNER)
                .orElseThrow(MatchDeleteForbiddenException::new);

        // 외래 키로 연결된 참가 기록을 먼저 삭제한 뒤 매치를 삭제한다. 삭제 전에 차감된 참가비를 환불한다.
        refundAllContributions(matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(matchId));
        matchParticipantRepository.deleteAllByMatch_Id(matchId);
        matchRepository.delete(match);
    }

    @Transactional
    public Long createMatch(Long userId, MatchCreateRequestDto req) {
        // 1. 생성 요청 회원 확인
        UserSummary user = userClient.findUser(userId)
            .orElseThrow(MatchUserNotFoundException::new);

        if (!user.isActive()) {
            throw new WithdrawnUserFoundException();
        }

        // Validate the catalog before creating either match or participant records.
        FacilityServiceSummary facility = facilityClient.findServiceById(req.getServiceId())
            .orElseThrow(ServiceNotFoundException::new);

        // 원격 조회 중 탈퇴가 커밋됐을 수 있으므로 저장 전에 로컬 상태를 잠가 확인한다.
        userGuard.requireActive(userId);

        // 크라우드펀딩 매치는 생성자도 1인당 참가비를 낸다. 그렇지 않으면 정원이 다 차도
        // 생성자 몫만큼 목표 금액에 못 미친다 (joinMatch와 같은 공식, 같은 차감 시점).
        Long contributionPoints = null;
        if (req.getTargetPoints() != null) {
            contributionPoints = (long) Math.ceil(req.getTargetPoints() / (double) req.getMaxParticipant());
            pointClient.deduct(userId, contributionPoints);
        }

        // 3. create MatchEntity and save
        MatchEntity savedMatchEntity =
            matchRepository.save(req.toEntity(facility.serviceId()));


        // 4. create MatchParticipant entity and save

        MatchParticipantEntity matchParticipantEntity
            = MatchParticipantEntity.builder()
            .userId(userId)
            .match(savedMatchEntity)
            .role(MatchParticipantRole.OWNER)
            .contributionPoints(contributionPoints)
            .build();

        matchParticipantRepository.save(matchParticipantEntity);


        // 5. return matchId
        return savedMatchEntity.getId();
    }

    @Transactional
    public MatchDetailResponseDto modifyMatch(
        Long matchId,
        Long userId,
        MatchModifyRequestDto req
    ) {
        // 참가·탈퇴·삭제와 같은 매치 행 잠금을 먼저 획득한다.
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);

        // 생성자 권한 확인
        matchParticipantRepository.findByMatch_IdAndUserId(matchId, userId)
                .filter(participant -> participant.getRole() == MatchParticipantRole.OWNER)
                .orElseThrow(MatchModifyForbiddenException::new);

        // 크라우드펀딩 매치는 참가비(1인당 목표금액/정원)가 가입 시점의 정원으로 고정된다.
        // 정원이 바뀌면 이미 낸 참가자와 새로 낼 참가자의 1인당 부담액이 달라져, 정원이 차도
        // 총 기여금이 목표 금액에 못 미칠 수 있다. 데모 범위에서는 재정산 대신 변경 자체를 막는다.
        if (match.getTargetPoints() != null && !req.getMaxParticipant().equals(match.getMaxParticipant())) {
            throw new FundedMatchCapacityImmutableException();
        }

        // OWNER를 포함한 현재 인원을 확인하고, 거절 시 어떤 필드도 변경하지 않는다.
        long participantCount = matchParticipantRepository.countByMatch_Id(matchId);
        if (req.getMaxParticipant() < participantCount) {
            throw new MatchCapacityBelowParticipantsException();
        }
        match.update(
            req.getTitle(),
            req.getDescription(),
            req.getStartAt(),
            req.getEndAt(),
            req.getMaxParticipant(),
            req.getSkillLevel(),
            req.getGenderGroup()
        );

        if (participantCount >= match.getMaxParticipant()) {
            match.closeRecruitment();
        } else if (match.getStartAt().isAfter(LocalDateTime.now(MATCH_TIME_ZONE))) {
            // 참가 탈퇴와 같은 기준으로, 수정 후 시작 시각이 지나지 않은 매치만 모집을 재개한다.
            match.reopenRecruitment();
        }

        // 응답용 원격 조회 실패는 이 트랜잭션 경계 안에서 처리해 변경을 커밋한다.
        return buildMatchDetail(match, userId, true);
    }

    public List<MatchResponseDto> searchMatches(MatchSearchRequestDto req) {

        String serviceId = null;
        if (req.getServiceId() != null) {
            FacilityServiceSummary facility = facilityClient.findServiceById(req.getServiceId()).orElse(null);
            if (facility == null) {
                return List.of();
            }
            serviceId = facility.serviceId();
        }

        // 지역·무료 여부 조건은 체육시설 서비스에 맞는 서울시 svcId를 먼저 묻는다.
        boolean filterByFacility = (req.getRegion() != null && !req.getRegion().isEmpty())
            || req.getIsFree() != null;
        List<String> facilityServiceIds = List.of("");
        if (filterByFacility) {
            facilityServiceIds = facilityClient.searchServiceIds(
                (req.getRegion() == null || req.getRegion().isEmpty()) ? null : req.getRegion(),
                req.getIsFree());
            if (facilityServiceIds.isEmpty()) {
                return List.of();
            }
        }

        List<MatchEntity> matches = matchRepository.searchMatches(
            serviceId,
            filterByFacility,
            facilityServiceIds,
            escapeSearchKeyword(req.getTitleKeyword()),
            escapeSearchKeyword(req.getDescriptionKeyword()),
            req.getStartAt(),
            req.getEndAt(),
            req.getMaxParticipant(),
            req.getSkillLevel(),
            req.getSportType(),
            req.getGenderGroup(),
            req.getStatus()
        );

        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = matches.stream().map(match -> match.getId()).toList();

        Map<Long, Long> countsByMatchId =
            matchRepository.countByMatchIds(matchIds).stream()
            .collect(Collectors.toMap(r -> r.getMatchId(), r -> r.getParticipantCount()));
        Map<String, FacilityServiceSummary> facilitiesById =
            findFacilities(matches.stream().map(MatchEntity::getServiceId).toList());


        return matches.stream()
            .map(match -> MatchResponseDto.toResponseDto(
                match,
                countsByMatchId.getOrDefault(match.getId(), 0L),
                facilitiesById.get(match.getServiceId())
            )).toList();

    }

    private Map<String, FacilityServiceSummary> findFacilities(Collection<String> serviceIds) {
        return facilityClient.findServices(serviceIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(FacilityServiceSummary::serviceId, Function.identity(),
                        (first, second) -> first));
    }

    private String escapeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }

        // Must match the repository's LIKE ESCAPE character; escape it first.
        return keyword.replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }
    /**
     * EM-03: 기본 정보, 참가자 프로필과 조회 사용자의 참여 상태를 반환한다.
     * OWNER도 참가 인원에 포함하며, 비로그인 요청의 userId는 null이다.
     */
    public MatchDetailResponseDto getMatchDetail(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);

        return buildMatchDetail(match, userId, false);
    }

    private MatchDetailResponseDto buildMatchDetail(
            MatchEntity match, Long userId, boolean tolerateRemoteFailure) {
        List<MatchParticipantEntity> participants =
                matchParticipantRepository.findAllByMatch_IdOrderByIdAsc(match.getId());


        List<Long> participantUserIds = participants.stream()
                .map(MatchParticipantEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();


        Map<Long, ProfileSummary> profilesByUserId;
        FacilityServiceSummary facility;
        try {
            profilesByUserId = participantUserIds.isEmpty()
                    ? Map.of()
                    : userClient.findProfiles(participantUserIds).stream()
                            .collect(Collectors.toMap(ProfileSummary::userId, Function.identity(),
                                    (first, second) -> first));
            facility = facilityClient.findService(match.getServiceId()).orElse(null);
        } catch (ExternalServiceUnavailableException e) {
            if (!tolerateRemoteFailure) {
                throw e;
            }
            // 수정 응답만 시설·프로필을 함께 비운다. 참가 인원·역할·권한은 유지한다.
            profilesByUserId = Map.of();
            facility = null;
        }

        List<MatchParticipantSummaryDto> participantSummaries = new ArrayList<>();
        boolean isOwner = false;
        boolean isParticipant = false;


        for (MatchParticipantEntity participant : participants) {
            ProfileSummary profile = participant.getUserId() == null
                    ? null : profilesByUserId.get(participant.getUserId());

            // 프로필이 누락되어도 참가 정보와 인원은 유지한다.
            participantSummaries.add(MatchParticipantSummaryDto.builder()
                    .profileId(profile == null ? null : profile.profileId())
                    .nickname(profile == null ? null : profile.nickname())
                    .imageUrl(profile == null ? null : profile.imageUrl())
                    .role(participant.getRole())
                    .build());

            if (userId != null && userId.equals(participant.getUserId())) {
                isParticipant = true;
                if (participant.getRole() == MatchParticipantRole.OWNER) {
                    isOwner = true;
                }
            }
        }

        return MatchDetailResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .description(match.getDescription())
                .startAt(match.getStartAt())
                .endAt(match.getEndAt())
                .maxParticipant(match.getMaxParticipant())
                .currentParticipantCount(participants.size())
                .status(match.getStatus())
                .skillLevel(match.getSkillLevel())
                .sportType(match.getSportType())
                .genderGroup(match.getGenderGroup())
                .serviceId(facility == null ? null : facility.id())
                .participants(participantSummaries)
                .isOwner(isOwner)
                .isParticipant(isParticipant)
                .region(facility == null ? null : facility.region())
                .serviceName(facility == null ? null : facility.serviceName())
                .locationName(facility == null ? null : facility.locationName())
                .build();
    }
}
