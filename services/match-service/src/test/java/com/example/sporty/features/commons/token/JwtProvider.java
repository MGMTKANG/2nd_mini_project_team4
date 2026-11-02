package com.example.sporty.features.commons.token;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 테스트 전용. 유저 서비스가 로그인 시 발급하는 토큰과 같은 형식을 만든다.
 * 실제 발급은 유저 서비스 책임이라 매치 서비스 본 코드에는 두지 않는다.
 */
public class JwtProvider {
    private static final Duration ACCESS_TOKEN_EXP = Duration.ofMinutes(30);
    private static final Duration REFRESH_TOKEN_EXP = Duration.ofDays(7);
    private final Key key;

    public JwtProvider(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long id) {
        return createToken(id, "ACCESS", ACCESS_TOKEN_EXP);
    }

    public String createRefreshToken(Long id) {
        return createToken(id, "REFRESH", REFRESH_TOKEN_EXP);
    }

    private String createToken(Long id, String tokenType, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(id.toString())
                .setId(UUID.randomUUID().toString())
                .claim("tokenType", tokenType)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }
}
