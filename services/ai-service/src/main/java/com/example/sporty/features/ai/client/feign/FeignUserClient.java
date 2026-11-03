package com.example.sporty.features.ai.client.feign;

import org.springframework.stereotype.Component;

import com.example.sporty.features.ai.client.UserClient;
import com.example.sporty.features.commons.exception.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import com.example.sporty.features.users.domain.dto.UserPreferenceDto;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignUserClient implements UserClient {

    private static final String SERVICE_NAME = "user-service";

    private final UserServiceFeignClient userServiceFeignClient;

    @Override
    public UserPreferenceDto findPreferences(Long userId) {
        try {
            UserPreferenceDto preference = userServiceFeignClient.findPreferences(userId);
            // 응답 모양이 약속과 다르면 연결 실패와 같게 본다
            if (preference == null) {
                throw new ExternalServiceUnavailableException(SERVICE_NAME);
            }
            return preference;
        } catch (FeignException.NotFound e) {
            // 없는 회원이거나 탈퇴한 회원
            throw new UserNotFoundException();
        } catch (FeignException e) {
            // 유저 서비스가 꺼져 있거나 오류를 낸 경우
            throw new ExternalServiceUnavailableException(SERVICE_NAME, e);
        }
    }
}