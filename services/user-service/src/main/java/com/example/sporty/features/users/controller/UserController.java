package com.example.sporty.features.users.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.dto.UserResponseDto;
import com.example.sporty.features.users.domain.dto.UserSignUpRequestDto;
import com.example.sporty.features.users.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "회원(User)", description = "회원가입, 정보조회, 회원탈퇴 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "신규 회원을 등록하고 프로필과 선호 종목을 저장합니다.")
    @PostMapping
    public ResponseEntity<UserResponseDto> signUp(@Valid @RequestBody UserSignUpRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signUp(request));
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 상세 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponseDto> getMyInfo(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getUserInfo(userId));
    }

    @Operation(summary = "회원 탈퇴", description = "회원 상태를 WITHDRAWN으로 변경하고 리프레시 토큰을 삭제합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
