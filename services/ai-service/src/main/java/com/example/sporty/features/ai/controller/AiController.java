package com.example.sporty.features.ai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.ai.agent.MatchAiAgent;
import com.example.sporty.features.ai.agent.MatchDraftAiAgent;
import com.example.sporty.features.ai.agent.MatchRecommendAiAgent;
import com.example.sporty.features.ai.domain.dto.AiRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final MatchAiAgent matchAIAgent;
    private final MatchDraftAiAgent matchDraftAiAgent;
    private final MatchRecommendAiAgent matchRecommendAiAgent;

    // AI-02  body : { "prompt" : "이번 주말 강남에서 풋살 초보 매치 찾아줘" }
    // AI가 조건을 추출하고 match-service에 검색을 요청한다.
    @PostMapping("/matches/search")
    public ResponseEntity<?> searchMatches(@Valid @RequestBody AiRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchAIAgent.search(request.getPrompt()));
    }

    // AI-01  body : { "prompt" : "토요일 저녁 7시 송파에서 농구 10명 모집" }
    // DB에 저장하지 않고 매치 개설 폼에 채울 초안만 반환한다.
    @PostMapping("/matches")
    public ResponseEntity<?> draftMatch(@Valid @RequestBody AiRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchDraftAiAgent.draft(request.getPrompt()));
    }

    // AI-03  로그인 회원의 선호 종목·활동 자치구·성별로 매치를 추천한다.
    // 내 매치 제외에 match-service /api/matches/me를 쓰므로 받은 Authorization 헤더를 그대로 넘긴다.
    @GetMapping("/matches/recommendations")
    public ResponseEntity<List<MatchResponseDto>> recommendMatches(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchRecommendAiAgent.recommend(userId, authorization));
    }
}