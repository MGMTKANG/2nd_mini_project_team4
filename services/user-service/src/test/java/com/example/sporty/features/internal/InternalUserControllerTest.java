package com.example.sporty.features.internal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;

@WebMvcTest(controllers = InternalUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private SportPreferenceRepository sportPreferenceRepository;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("AI 추천용 회원 선호 정보 조회 성공 - 성별, 자치구 한글명, 선호종목 반환")
    void getUserPreferences_Success() throws Exception {
        // given
        Long userId = 1L;
        UserEntity user = UserEntity.builder()
                .email("test@example.com")
                .password("encoded_pass")
                .gender(Gender.FEMALE)
                .build();
        // ID 강제 설정을 위해 리플렉션 대신 Builder/Entity 상태 모킹
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        ProfileEntity profile = ProfileEntity.builder()
                .userId(userId)
                .nickname("러너")
                .district(District.GANGNAM)
                .build();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        // profile.getId()가 null일 수 있으므로 mock repository 동작 정의
        given(sportPreferenceRepository.findByProfileId(profile.getId())).willReturn(List.of(
                SportPreferenceEntity.builder().profileId(profile.getId()).sportType(SportType.FUTSAL).build(),
                SportPreferenceEntity.builder().profileId(profile.getId()).sportType(SportType.TENNIS).build()
        ));

        // when & then
        mockMvc.perform(get("/internal/users/{userId}/preferences", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.district").value("강남구"))
                .andExpect(jsonPath("$.preferenceSports[0]").value("FUTSAL"))
                .andExpect(jsonPath("$.preferenceSports[1]").value("TENNIS"));
    }

    @Test
    @DisplayName("AI 추천용 회원 선호 정보 조회 - 존재하지 않는 회원은 404 Not Found")
    void getUserPreferences_NotFound() throws Exception {
        // given
        Long userId = 999L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/internal/users/{userId}/preferences", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AI 추천용 회원 선호 정보 조회 - 탈퇴 회원은 404 Not Found")
    void getUserPreferences_WithdrawnUser_NotFound() throws Exception {
        // given
        Long userId = 2L;
        UserEntity user = UserEntity.builder()
                .email("withdrawn@example.com")
                .password("encoded_pass")
                .gender(Gender.MALE)
                .build();
        user.withdraw();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when & then
        mockMvc.perform(get("/internal/users/{userId}/preferences", userId))
                .andExpect(status().isNotFound());
    }
}
