package com.example.sporty.features.ai.client.feign;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.sporty.features.ai.client.MatchClient;
import com.example.sporty.features.commons.exception.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.ai.AiSearchException;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignMatchClient implements MatchClient {

    private static final String SERVICE_NAME = "match-service";
    private static final String INVALID_CONDITION_MESSAGE =
            "검색 조건을 확인해주세요. (예: 종료 일시는 시작 일시보다 뒤여야 합니다)";

    private final MatchServiceFeignClient matchServiceFeignClient;

    @Override
    public List<MatchResponseDto> searchMatches(MatchSearchRequestDto condition) {
        try {
            List<MatchResponseDto> matches = matchServiceFeignClient.searchMatches(condition);
            // 응답 모양이 약속과 다르면 연결 실패와 같게 본다
            if (matches == null || matches.stream().anyMatch(Objects::isNull)) {
                throw new ExternalServiceUnavailableException(SERVICE_NAME);
            }
            return matches;
        } catch (FeignException.BadRequest e) {
            // AI가 뽑은 조건을 match-service가 거절한 경우 (예: 종료 일시가 시작 일시보다 앞)
            throw new AiSearchException(INVALID_CONDITION_MESSAGE);
        } catch (FeignException e) {
            // 매치 서비스가 꺼져 있거나 오류를 낸 경우
            throw new ExternalServiceUnavailableException(SERVICE_NAME, e);
        }
    }
}