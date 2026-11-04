package com.example.sporty.features.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.controller.ProfileController;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.profiles.service.ProfileService;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;

@WebMvcTest(controllers = ProfileController.class, properties = {
    "jwt.secret=profile-test-only-signing-key-with-at-least-32-bytes",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class, ProfileService.class})
class ProfileDetailIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockitoBean ProfileRepository profiles;
    @MockitoBean SportPreferenceRepository sports;

    private ProfileEntity profile(long profileId, long userId) {
        ProfileEntity p = ProfileEntity.builder().userId(userId).nickname("참가자")
            .district(District.GANGNAM).imageUrl("https://example.com/profile.png").build();
        ReflectionTestUtils.setField(p, "id", profileId);
        return p;
    }

    private String access(long userId) {
        return "Bearer " + jwt.createAccessToken(userId);
    }

    @Test
    void readsOtherParticipantsProfileByProfileIdNotUserId() throws Exception {
        when(profiles.findById(91L)).thenReturn(Optional.of(profile(91L, 23L)));
        when(sports.findByProfileId(91L)).thenReturn(List.of(
            SportPreferenceEntity.builder().profileId(91L).sportType(SportType.TENNIS).build()));
        mvc.perform(get("/api/profiles/91").header("Authorization", access(7L)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(91))
            .andExpect(jsonPath("$.nickname").value("참가자"))
            .andExpect(jsonPath("$.district").value("GANGNAM"))
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/profile.png"))
            .andExpect(jsonPath("$.preferenceSports[0]").value("TENNIS"))
            .andExpect(jsonPath("$.userId").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist());
        verify(profiles, never()).findByUserId(anyLong());
        verify(sports).findByProfileId(91L);
    }

    @Test
    void returnsEmptyPreferencesAndNullImageWhenAbsent() throws Exception {
        ProfileEntity p = profile(91L, 23L);
        p.update("참가자", District.GANGNAM, null);
        when(profiles.findById(91L)).thenReturn(Optional.of(p));
        when(sports.findByProfileId(91L)).thenReturn(List.of());
        mvc.perform(get("/api/profiles/91").header("Authorization", access(7L)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferenceSports").isEmpty())
            .andExpect(jsonPath("$.imageUrl").isEmpty());
    }

    @Test
    void missingProfileReturns404WithoutPreferenceLookup() throws Exception {
        when(profiles.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/profiles/999").header("Authorization", access(7L)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("프로필이 존재하지 않습니다."));
        verifyNoInteractions(sports);
    }

    @Test
    void missingTokenReturns401WithoutDatabaseLookup() throws Exception {
        mvc.perform(get("/api/profiles/91")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles, sports);
    }

    @Test
    void malformedTokenReturns401WithoutDatabaseLookup() throws Exception {
        mvc.perform(get("/api/profiles/91").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles, sports);
    }

    @Test
    void refreshTokenCannotReadProfile() throws Exception {
        mvc.perform(get("/api/profiles/91").header("Authorization", "Bearer " + jwt.createRefreshToken(7L)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles, sports);
    }

    @Test
    void meStillUsesAuthenticatedUserIdInsteadOfProfileId() throws Exception {
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile(41L, 7L)));
        when(sports.findByProfileId(41L)).thenReturn(List.of());
        mvc.perform(get("/api/profiles/me").header("Authorization", access(7L)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(41));
        verify(profiles).findByUserId(7L);
        verify(profiles, never()).findById(anyLong());
    }

    @Test
    void updateMeStillChangesOnlyAuthenticatedUsersProfile() throws Exception {
        ProfileEntity own = profile(41L, 7L);
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(own));
        mvc.perform(put("/api/profiles/me").header("Authorization", access(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"변경닉네임\",\"district\":\"GANGNAM\",\"sportTypes\":[\"TENNIS\"],\"userId\":23}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("변경닉네임"));
        assertThat(own.getUserId()).isEqualTo(7L);
        verify(profiles).findByUserId(7L);
        verify(profiles, never()).findByUserId(23L);
        verify(sports).deleteByProfileId(41L);
    }
}
