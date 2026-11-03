package com.example.sporty.features.ai.client.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.sporty.features.users.domain.dto.UserPreferenceDto;

// 유저 서비스 내부 API. Gateway를 거치지 않고 서비스끼리 직접 호출한다.
@FeignClient(name = "user-service", url = "${clients.user-service.url:}")
public interface UserServiceFeignClient {

    @GetMapping("/internal/users/{userId}/preferences")
    UserPreferenceDto findPreferences(@PathVariable("userId") Long userId);
}