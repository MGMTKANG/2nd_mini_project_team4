package com.example.sporty.features.exerciseMatching.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// match-service GET /api/matches 검색 조건. AI가 추출하는 항목만 담는다.
// 칸 이름이 쿼리 파라미터 이름이 되므로 match-service의 MatchSearchRequestDto와 같아야 한다.
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MatchSearchRequestDto {

    private SportType sportType;
    private String region;
    private LocalDateTime startAt, endAt;
    private SkillLevel skillLevel;
    private GenderGroup genderGroup;
    private MatchStatus status;
}