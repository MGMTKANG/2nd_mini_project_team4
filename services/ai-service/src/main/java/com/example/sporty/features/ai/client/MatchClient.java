package com.example.sporty.features.ai.client;

import java.util.List;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MyMatchResponseDto;

/** AI 서비스가 매치 서비스에 요청하는 매치 조회. */
public interface MatchClient {

    // 조건에 맞는 매치 목록. 비어 있는 조건은 검색에 쓰지 않는다.
    List<MatchResponseDto> searchMatches(MatchSearchRequestDto condition);

    // 로그인 회원이 만들었거나 참가한 매치. 사용자의 JWT를 그대로 전달한다.
    List<MyMatchResponseDto> findMyMatches(String authorization);
}