package com.example.sporty.features.ai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.ai.agent.MatchDraftAiAgent;
import com.example.sporty.features.ai.domain.dto.AiRequestDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final MatchDraftAiAgent matchDraftAiAgent;

    // AI-01  body : { "prompt" : "토요일 저녁 7시 송파에서 농구 10명 모집" }
    // DB에 저장하지 않고 매치 개설 폼에 채울 초안만 반환한다.
    @PostMapping("/matches")
    public ResponseEntity<?> draftMatch(@Valid @RequestBody AiRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(matchDraftAiAgent.draft(request.getPrompt()));
    }
}