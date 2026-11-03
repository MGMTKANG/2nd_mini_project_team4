package com.example.sporty.features.ai.client;

import com.example.sporty.features.users.domain.dto.UserPreferenceDto;

/** AI 서비스가 유저 서비스에 요청하는 회원 정보 조회. */
public interface UserClient {

    // 추천에 쓰는 성별·활동 자치구·선호 종목
    UserPreferenceDto findPreferences(Long userId);
}