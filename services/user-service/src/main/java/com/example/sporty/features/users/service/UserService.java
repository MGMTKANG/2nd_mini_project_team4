package com.example.sporty.features.users.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.commons.exception.users.DuplicateEmailException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.commons.exception.users.UserNotFoundException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;
import com.example.sporty.features.users.domain.dto.UserInfoResponseDto;
import com.example.sporty.features.users.domain.dto.UserResponseDto;
import com.example.sporty.features.users.domain.dto.UserSignUpRequestDto;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.event.UserWithdrawnEvent;
import com.example.sporty.features.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final SportPreferenceRepository sportPreferenceRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponseDto signUp(UserSignUpRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }
        if (profileRepository.existsByNickname(request.getNickname())) {
            throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        UserEntity user = UserSignUpRequestDto.toUserEntity(request, encodedPassword);
        UserEntity savedUser = userRepository.save(user);

        ProfileEntity profile = ProfileEntity.builder()
                .userId(savedUser.getId())
                .nickname(request.getNickname())
                .district(request.getDistrict())
                .build();
        ProfileEntity savedProfile = profileRepository.save(profile);

        if (request.getSportTypes() != null && !request.getSportTypes().isEmpty()) {
            List<SportPreferenceEntity> preferences = request.getSportTypes().stream()
                    .distinct()
                    .map(sportType -> SportPreferenceEntity.builder()
                            .profileId(savedProfile.getId())
                            .sportType(sportType)
                            .build())
                    .toList();
            sportPreferenceRepository.saveAll(preferences);
        }

        log.info("회원가입 완료: userId={}, email={}", savedUser.getId(), savedUser.getEmail());
        return UserResponseDto.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserInfoResponseDto getUserInfo(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElse(null);

        List<SportType> sports = Collections.emptyList();
        if (profile != null) {
            sports = sportPreferenceRepository.findByProfileId(profile.getId()).stream()
                    .map(SportPreferenceEntity::getSportType)
                    .toList();
        }

        return UserInfoResponseDto.fromEntity(user, profile, sports);
    }

    @Transactional
    public void withdraw(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (!user.isActive()) {
            throw new IllegalStateException("이미 탈퇴 처리된 회원입니다.");
        }

        user.withdraw();
        refreshTokenService.delete(userId);
        // Kafka 발행은 트랜잭션 커밋 후 리스너에서 처리한다.
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId, UUID.randomUUID(), Instant.now()));

        log.info("회원 탈퇴 처리 완료: userId={}", userId);
    }
}
