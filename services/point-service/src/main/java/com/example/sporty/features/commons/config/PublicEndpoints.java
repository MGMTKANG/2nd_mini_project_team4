package com.example.sporty.features.commons.config;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;

/** Shared by authorization and JWT filtering so public routes cannot drift apart. */
public final class PublicEndpoints {
    private PublicEndpoints() {}

    // /internal/** 는 서비스 간 인증 방식 확정 전까지 임시로 공개해둔다.
    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/deduct"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/refund"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/cancel"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/operations/deduct"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/operations/refund"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/points/{userId}/operations/cancel"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/liveness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/readiness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/info"));
}
