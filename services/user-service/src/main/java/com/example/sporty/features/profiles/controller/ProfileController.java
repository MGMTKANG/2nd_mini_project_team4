package com.example.sporty.features.profiles.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.profiles.domain.dto.ProfileDetailResponseDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateRequestDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateResponseDto;
import com.example.sporty.features.profiles.service.ProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "프로필(Profile)", description = "회원 프로필 및 선호 종목 관리 API")
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "내 프로필 조회", description = "현재 사용자의 닉네임, 지역, 선호 종목 등을 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ProfileDetailResponseDto> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @Operation(summary = "참가자 프로필 조회", description = "프로필 ID로 닉네임, 지역, 선호 종목 등을 조회합니다.")
    @GetMapping("/{profileId}")
    public ResponseEntity<ProfileDetailResponseDto> getProfile(@PathVariable Long profileId) {
        return ResponseEntity.ok(profileService.getProfileById(profileId));
    }

    @Operation(summary = "프로필 수정", description = "닉네임, 활동 지역, 선호 종목을 변경합니다.")
    @PutMapping("/me")
    public ResponseEntity<ProfileUpdateResponseDto> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProfileUpdateRequestDto request) {
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }
}
