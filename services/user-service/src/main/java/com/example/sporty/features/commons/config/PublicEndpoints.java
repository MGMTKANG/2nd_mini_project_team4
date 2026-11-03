package com.example.sporty.features.commons.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class PublicEndpoints {
    private PublicEndpoints() {}

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/users"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/internal/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/internal/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
            PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/actuator/**")
    );
}
