package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties (ignoreUnknown = true)
public class MatchResponseDto {

    private Long matchId;

    private String title;
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime reservationDeadlineAt;

    private Integer numCurrentParticipant;
    private Integer maxParticipant;
    private Double distance;

    private MatchStatus status;
    private SkillLevel skillLevel;
    private SportType sportType;

    private GenderGroup genderGroup;
    private String region;
    @JsonProperty("isFree")
    private Boolean isFree;

    // 크라우드펀딩 목표 금액. 값이 없으면(null) 프론트가 펀딩 UI 자체를 숨긴다.
    private Long targetPoints;

    // 시설 정보를 받지 못한 매치도 목록에서 빠지지 않도록 시설 항목만 null로 둔다.
    public static MatchResponseDto toResponseDto(MatchEntity entity, long participantCount,
            FacilityServiceSummary facility) {
        return MatchResponseDto.builder()
            .matchId(entity.getId())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .startAt(entity.getStartAt())
            .endAt(entity.getEndAt())
            .reservationDeadlineAt(facility == null ? null : facility.reservationDeadlineAt())
            .numCurrentParticipant(Math.toIntExact(participantCount))
            .maxParticipant(entity.getMaxParticipant())
            .targetPoints(entity.getTargetPoints())
            .status(entity.getStatus())
            .skillLevel(entity.getSkillLevel())
            .sportType(entity.getSportType())
            .genderGroup(entity.getGenderGroup())
            .region(facility == null ? null : facility.region())
            .isFree(facility == null ? null : facility.isFree())
            .distance(null) // TODO
            .build();
    }
}
