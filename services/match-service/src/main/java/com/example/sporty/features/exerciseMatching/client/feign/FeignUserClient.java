package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.sporty.features.exerciseMatching.client.UserClient;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignUserClient implements UserClient {

    private final UserServiceFeignClient userServiceFeignClient;

    @Override
    public Optional<UserSummary> findUser(Long userId) {
        try {
            return Optional.ofNullable(userServiceFeignClient.getUser(userId));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProfileSummary> findProfiles(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userServiceFeignClient.getProfiles(userIds);
    }
}
