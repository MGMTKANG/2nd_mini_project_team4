package com.example.sporty.features.exerciseMatching.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// match-service GET /api/matches/me 응답 한 건. 추천에서 참가 매치를 빼는 데 매치 ID만 쓴다.
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyMatchResponseDto {

    private Long matchId;
}