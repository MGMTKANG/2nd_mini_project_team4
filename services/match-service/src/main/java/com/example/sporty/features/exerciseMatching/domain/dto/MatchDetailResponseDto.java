package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EM-03 운동 매칭 상세 화면에 필요한 응답 정보.
 * 장소 정보는 체육시설 서비스에서 받지 못하면 null로 반환한다.
 */
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailResponseDto {

    private Long matchId;
    private String title;
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private Integer maxParticipant;
    private Integer currentParticipantCount;

    private MatchStatus status;
    private SkillLevel skillLevel;
    private SportType sportType;
    private GenderGroup genderGroup;

    private Long serviceId;
    private String serviceName;
    private String locationName;
    private String region;

    // 크라우드펀딩 목표 금액. 값이 없으면(null) 프론트가 펀딩 UI 자체를 숨긴다.
    private Long targetPoints;
    // 참가 기록에 남은 실제 기여금 합계. 환불 진행 중에도 인원으로 추정하지 않는다.
    private Long fundedPoints;
    private boolean fundingFailed;

    private List<MatchParticipantSummaryDto> participants;

    // 현재 조회 사용자 기준. 비로그인은 둘 다 false, OWNER는 둘 다 true다.
    @JsonProperty("isOwner")
    private Boolean isOwner;

    @JsonProperty("isParticipant")
    private Boolean isParticipant;
}
