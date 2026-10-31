package com.example.sporty.features.exerciseMatching.client;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 매치 서비스가 유저 서비스에 요청하는 회원·프로필 조회. */
public interface UserClient {

    Optional<UserSummary> findUser(Long userId);

    // 상세 화면 참가자 목록용. 프로필이 없는 회원은 결과에서 빠진다.
    List<ProfileSummary> findProfiles(Collection<Long> userIds);

    record UserSummary(Long userId, String status) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    record ProfileSummary(Long userId, Long profileId, String nickname, String imageUrl) {
    }
}
