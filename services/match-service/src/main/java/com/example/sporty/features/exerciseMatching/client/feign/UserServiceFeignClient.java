package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.sporty.features.exerciseMatching.client.UserClient.ProfileSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;

// 유저 서비스 내부 API 제안안. 경로와 응답 형식은 유저 서비스 담당자와 확정해야 한다.
@FeignClient(name = "user-service", url = "${clients.user-service.url:}")
public interface UserServiceFeignClient {

    @GetMapping("/internal/users/{userId}")
    UserSummary getUser(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/profiles")
    List<ProfileSummary> getProfiles(@RequestParam("userIds") Collection<Long> userIds);
}
