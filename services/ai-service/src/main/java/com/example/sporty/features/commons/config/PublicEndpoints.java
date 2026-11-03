package com.example.sporty.features.commons.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

// 로그인 없이 지나가는 경로. SecurityConfig와 JwtAuthenticationFilter가 같이 써서 목록이 어긋나지 않게 한다.
public final class PublicEndpoints {
    private PublicEndpoints() {}

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            // 자연어 검색은 1차처럼 로그인 없이 사용
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/ai/matches/search"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health"),
            // K8s가 인증 없이 확인하는 경로
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/liveness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/readiness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/info"));
}