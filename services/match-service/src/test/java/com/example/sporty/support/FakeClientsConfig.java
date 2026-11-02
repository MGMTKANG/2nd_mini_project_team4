package com.example.sporty.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.example.sporty.features.exerciseMatching.service.MatchUserGuard;

/** 저장소 테스트에서 유저·체육시설 서비스 호출을 메모리 구현으로 대체한다. */
@Import(MatchUserGuard.class)
@TestConfiguration
public class FakeClientsConfig {

    @Bean
    FakeUserClient fakeUserClient() {
        return new FakeUserClient();
    }

    @Bean
    FakeFacilityClient fakeFacilityClient() {
        return new FakeFacilityClient();
    }
}
