package com.example.sporty.features.exerciseMatching.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.exerciseMatching.service.MatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 데모용 내부 트리거. 실제 마감 판정 배치/크론 대신 수동으로 SAGA 보상 트랜잭션을 시연한다.
@Tag(name = "내부 통신 API (Internal)", description = "펀딩 실패(정원 미달) 보상 트랜잭션을 수동으로 트리거하는 데모용 API")
@RestController
@RequestMapping("/internal/matches")
@RequiredArgsConstructor
public class MatchInternalController {

    private final MatchService matchService;

    @Operation(summary = "펀딩 실패 처리 (데모용 수동 트리거)",
            description = "매치를 마감하고, 참가비를 낸 참가자 전원에게 point-service 환불 API를 호출한다.")
    @PostMapping("/{matchId}/fail-funding")
    public ResponseEntity<Void> failFunding(@PathVariable("matchId") Long matchId) {
        matchService.failFunding(matchId);
        return ResponseEntity.noContent().build();
    }
}
