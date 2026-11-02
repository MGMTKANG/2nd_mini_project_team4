package com.example.sporty.features.commons.config;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;

/** Shared by authorization and JWT filtering so public routes cannot drift apart. */
public final class PublicEndpoints {
    private PublicEndpoints() {}

    // 체육시설 조회는 비로그인으로 공개한다 (원본 모놀리스와 동일).
    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/services"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/services/{serviceId}"),
            // devops가 K8s probe 등으로 쓸 헬스체크 경로 (match-service와 동일 컨벤션).
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/liveness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/readiness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/info"));
}
