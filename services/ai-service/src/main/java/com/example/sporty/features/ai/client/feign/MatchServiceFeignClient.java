package com.example.sporty.features.ai.client.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MyMatchResponseDto;

// 매치 서비스 공개 API. 공통 규약 제안대로 GET /api/matches를 사용한다.
@FeignClient(name = "match-service", url = "${clients.match-service.url:}")
public interface MatchServiceFeignClient {

    @GetMapping("/api/matches")
    List<MatchResponseDto> searchMatches(@SpringQueryMap MatchSearchRequestDto condition);

    // 내 매치 목록은 로그인이 필요해서 사용자의 Authorization 헤더를 그대로 붙인다
    @GetMapping("/api/matches/me")
    List<MyMatchResponseDto> findMyMatches(@RequestHeader("Authorization") String authorization);
}