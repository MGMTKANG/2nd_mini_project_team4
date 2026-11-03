package com.example.sporty.features.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, long expirationSeconds) {
        redisTemplate.opsForValue().set(
                "refresh:" + userId,
                refreshToken,
                Duration.ofSeconds(expirationSeconds));
    }

    public void delete(Long userId) {
        redisTemplate.delete("refresh:" + userId);
    }

    public String findByUserId(Long userId) {
        return redisTemplate.opsForValue().get("refresh:" + userId);
    }
}
