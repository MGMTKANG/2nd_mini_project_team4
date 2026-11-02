package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
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
            UserSummary user = userServiceFeignClient.getUser(userId);
            if (user == null || user.userId() == null || user.status() == null) {
                throw new ExternalServiceUnavailableException("user-service");
            }
            return Optional.of(user);
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("user-service", e);
        }
    }

    @Override
    public List<ProfileSummary> findProfiles(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        try {
            List<ProfileSummary> profiles = userServiceFeignClient.getProfiles(userIds);
            if (profiles == null || profiles.stream().anyMatch(profile ->
                    profile == null || profile.userId() == null)) {
                throw new ExternalServiceUnavailableException("user-service");
            }
            return profiles;
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("user-service", e);
        }
    }
}
