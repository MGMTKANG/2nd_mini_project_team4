package com.example.sporty.features.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.internal.dto.ProfileSummaryDto;
import com.example.sporty.features.internal.dto.UserSummaryDto;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "내부 통신 API (Internal)", description = "match-service 등 타 마이크로서비스 Feign 전용 내부 조회 API")
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Operation(summary = "회원 상태 조회 (Feign 전용)", description = "match-service에서 참가자 유효성 및 탈퇴 여부를 검증하기 위해 호출합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserSummaryDto> getUser(@PathVariable("userId") Long userId) {
        log.debug("내부 회원 상태 조회 호출: userId={}", userId);
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(new UserSummaryDto(user.getId(), user.getStatus().name())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "참가자 프로필 목록 일괄 조회 (Feign 전용)", description = "match-service에서 매치 상세 화면 참가자 프로필 카드 표시를 위해 호출합니다.")
    @GetMapping("/profiles")
    public ResponseEntity<List<ProfileSummaryDto>> getProfiles(@RequestParam("userIds") Collection<Long> userIds) {
        log.debug("내부 프로필 목록 일괄 조회 호출: userIds={}", userIds);
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<ProfileEntity> profiles = profileRepository.findByUserIdIn(userIds);
        List<ProfileSummaryDto> dtos = profiles.stream()
                .map(p -> new ProfileSummaryDto(p.getUserId(), p.getId(), p.getNickname(), p.getImageUrl()))
                .toList();

        return ResponseEntity.ok(dtos);
    }
}
