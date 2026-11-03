package com.example.sporty.features.ai.client;

import java.util.List;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;

/** AI 서비스가 매치 서비스에 요청하는 매치 조회. */
public interface MatchClient {

    // 조건에 맞는 매치 목록. 비어 있는 조건은 검색에 쓰지 않는다.
    List<MatchResponseDto> searchMatches(MatchSearchRequestDto condition);
}