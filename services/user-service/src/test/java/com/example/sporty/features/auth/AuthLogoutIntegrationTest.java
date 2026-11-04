package com.example.sporty.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.auth.controller.AuthController;
import com.example.sporty.features.auth.service.AuthService;
import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AuthController.class, properties = {
    "jwt.secret=logout-test-only-signing-key-with-at-least-32-bytes",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class,
         AuthService.class, RefreshTokenService.class})
class AuthLogoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @MockitoBean UserRepository users;
    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> tokens.get(i.getArgument(0)));
        doAnswer(i -> { tokens.put(i.getArgument(0), i.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(i -> tokens.remove(i.getArgument(0)) != null);
    }

    @Test
    void logoutDeletesOnlyAuthenticatedUsersTokenAndReturnsEmpty204() throws Exception {
        tokens.put("refresh:7", "own-token");
        tokens.put("refresh:8", "other-token");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jwt.createAccessToken(7L))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":8}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(tokens).containsOnlyKeys("refresh:8");
        verify(redis).delete("refresh:7");
        verify(redis, never()).delete("refresh:8");
    }

    @Test
    void repeatedLogoutWithoutSavedTokenStillReturns204() throws Exception {
        String access = jwt.createAccessToken(7L);
        for (int n = 0; n < 2; n++) {
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        }
    }

    @Test
    void missingAccessTokenIsRejectedBeforeDeletion() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void refreshTokenCannotAuthorizeLogout() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jwt.createRefreshToken(7L)))
            .andExpect(status().isUnauthorized());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void malformedAccessTokenIsRejectedBeforeDeletion() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void loginAndRefreshWorkButRefreshAfterLogoutIsRejected() throws Exception {
        UserEntity user = UserEntity.builder().email("logout@example.com")
            .password(encoder.encode("Test-password-123!")).gender(Gender.MALE).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail("logout@example.com")).thenReturn(Optional.of(user));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"logout@example.com\",\"password\":\"Test-password-123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = json.readTree(login).get("refreshToken").asText();
        String access = json.readTree(login).get("accessToken").asText();
        String request = json.writeValueAsString(Map.of("refreshToken", refresh));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isUnauthorized());
        assertThat(tokens).doesNotContainKey("refresh:7");
    }
}
