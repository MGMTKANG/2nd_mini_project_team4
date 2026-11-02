package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
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

// match-service GET /api/matches 응답 한 건. 받는 쪽이라 칸 이름만 맞춘다.
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
}