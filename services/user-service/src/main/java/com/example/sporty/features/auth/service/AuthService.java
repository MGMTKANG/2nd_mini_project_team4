package com.example.sporty.features.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.auth.domain.dto.LoginRequestDto;
import com.example.sporty.features.auth.domain.dto.LoginResponseDto;
import com.example.sporty.features.auth.domain.dto.RefreshRequestDto;
import com.example.sporty.features.auth.domain.dto.RefreshResponseDto;
import com.example.sporty.features.commons.exception.auth.InvalidRefreshTokenException;
import com.example.sporty.features.commons.exception.auth.LoginFailException;
import com.example.sporty.features.commons.token.JwtProvider;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new LoginFailException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new LoginFailException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!user.isActive()) {
            throw new LoginFailException("탈퇴하거나 비활성화된 계정입니다.");
        }

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenService.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenExpirationSeconds());

        return LoginResponseDto.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    @Transactional
    public RefreshResponseDto refresh(RefreshRequestDto request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new InvalidRefreshTokenException("리프레시 토큰이 필요합니다.");
        }

        Long userId = jwtProvider.validateRefreshToken(request.getRefreshToken());
        String savedToken = refreshTokenService.findByUserId(userId);

        if (savedToken == null || !savedToken.equals(request.getRefreshToken())) {
            throw new InvalidRefreshTokenException("유효하지 않거나 이미 만료된 리프레시 토큰입니다.");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!user.isActive()) {
            throw new InvalidRefreshTokenException("탈퇴한 회원의 토큰입니다.");
        }

        String newAccessToken = jwtProvider.createAccessToken(userId);
        return RefreshResponseDto.builder()
                .accessToken(newAccessToken)
                .build();
    }
}
