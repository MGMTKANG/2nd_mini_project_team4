package com.example.sporty.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.example.sporty.features.exerciseMatching.client.UserClient;

/** 유저 서비스 대신 회원과 프로필을 메모리에 보관한다. */
public class FakeUserClient implements UserClient {

    // DB 생성 ID처럼 테스트마다 겹치지 않도록 계속 증가시킨다.
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, UserSummary> users = new ConcurrentHashMap<>();
    private final Map<Long, ProfileSummary> profiles = new ConcurrentHashMap<>();

    public Long addActiveUser() {
        Long userId = sequence.getAndIncrement();
        users.put(userId, new UserSummary(userId, "ACTIVE"));
        return userId;
    }

    public void withdraw(Long userId) {
        users.put(userId, new UserSummary(userId, "WITHDRAWN"));
    }

    public void addProfile(ProfileSummary profile) {
        profiles.put(profile.userId(), profile);
    }

    @Override
    public Optional<UserSummary> findUser(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public List<ProfileSummary> findProfiles(Collection<Long> userIds) {
        return userIds.stream().map(profiles::get).filter(profile -> profile != null).toList();
    }
}
