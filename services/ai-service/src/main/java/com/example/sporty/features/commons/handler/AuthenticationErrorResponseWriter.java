package com.example.sporty.features.commons.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

public final class AuthenticationErrorResponseWriter {
    private AuthenticationErrorResponseWriter() {}

    public static void writeUnauthorized(HttpServletResponse response, ObjectMapper objectMapper)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .code("UNAUTHORIZED")
                .message("인증에 실패했습니다.")
                .build());
    }
}
