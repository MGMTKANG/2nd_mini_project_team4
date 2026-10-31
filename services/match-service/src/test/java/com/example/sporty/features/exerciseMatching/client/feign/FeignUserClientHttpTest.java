package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.UserClient.ProfileSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;
import com.example.sporty.support.LocalHttpStub;
import com.example.sporty.support.LocalHttpStub.Request;

import feign.FeignException;
import feign.RetryableException;

// 팀 공통 규약 초안(2026-10-31, 합의 전) 3·4절의 HTTP 계약.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-service;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.cloud.openfeign.client.config.default.connect-timeout=1000",
        "spring.cloud.openfeign.client.config.default.read-timeout=500"
})
class FeignUserClientHttpTest {

    private static final LocalHttpStub USER_SERVICE = new LocalHttpStub();
    private static final LocalHttpStub FACILITY_SERVICE = new LocalHttpStub();

    @Autowired
    private UserClient userClient;

    @Autowired
    private FacilityClient facilityClient;

    @DynamicPropertySource
    static void clientUrls(DynamicPropertyRegistry registry) {
        registry.add("clients.user-service.url", USER_SERVICE::baseUrl);
        registry.add("clients.facility-service.url", FACILITY_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetStubsAndVerifyRealClients() {
        assertThat(userClient).isInstanceOf(FeignUserClient.class);
        assertThat(facilityClient).isInstanceOf(FeignFacilityClient.class);
        USER_SERVICE.reset();
        FACILITY_SERVICE.reset();
    }

    @AfterAll
    static void stopStubs() {
        USER_SERVICE.close();
        FACILITY_SERVICE.close();
    }

    @Test
    void findUserMapsActiveUser() {
        USER_SERVICE.stub("/internal/users/1", 200, """
                {"userId":1,"status":"ACTIVE"}
                """);

        UserSummary user = userClient.findUser(1L).orElseThrow();

        assertThat(user).isEqualTo(new UserSummary(1L, "ACTIVE"));
        assertThat(user.isActive()).isTrue();
        assertRequest("/internal/users/1", Map.of());
    }

    @Test
    void findUserMapsWithdrawnUserAsInactive() {
        USER_SERVICE.stub("/internal/users/1", 200, """
                {"userId":1,"status":"WITHDRAWN"}
                """);

        UserSummary user = userClient.findUser(1L).orElseThrow();

        assertThat(user).isEqualTo(new UserSummary(1L, "WITHDRAWN"));
        assertThat(user.isActive()).isFalse();
        assertRequest("/internal/users/1", Map.of());
    }

    @Test
    void findUserReturnsEmptyOnlyForNotFound() {
        USER_SERVICE.stub("/internal/users/1", 404, """
                {"code":"USER_NOT_FOUND","message":"회원이 없습니다"}
                """);

        assertThat(userClient.findUser(1L)).isEmpty();

        assertRequest("/internal/users/1", Map.of());
    }

    @Test
    void findUserPropagatesServerError() {
        USER_SERVICE.stub("/internal/users/1", 500, """
                {"code":"INTERNAL_SERVER_ERROR","message":"유저 서비스 오류"}
                """);

        assertThatThrownBy(() -> userClient.findUser(1L))
                .isInstanceOf(FeignException.InternalServerError.class);

        assertRequest("/internal/users/1", Map.of());
    }

    @Test
    void findUserPropagatesReadTimeout() {
        // 테스트 전용 응답 대기 500ms보다 길게 지연한다. 운영 설정은 변경하지 않는다.
        USER_SERVICE.stub("/internal/users/1", 200, """
                {"userId":1,"status":"ACTIVE"}
                """, Duration.ofSeconds(2));

        assertThatThrownBy(() -> userClient.findUser(1L))
                .isInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);

        assertRequest("/internal/users/1", Map.of());
    }

    @Test
    void findProfilesUsesRepeatedUserIdsAndMapsAllFieldsIncludingNullImageUrl() {
        USER_SERVICE.stub("/internal/users/profiles", 200, """
                [
                  {"userId":1,"profileId":10,"nickname":"운동친구","imageUrl":"/images/1.png"},
                  {"userId":2,"profileId":20,"nickname":"새친구","imageUrl":null}
                ]
                """);

        List<ProfileSummary> profiles = userClient.findProfiles(List.of(1L, 2L));

        assertThat(profiles).containsExactly(
                new ProfileSummary(1L, 10L, "운동친구", "/images/1.png"),
                new ProfileSummary(2L, 20L, "새친구", null));
        assertRequest("/internal/users/profiles", Map.of("userIds", List.of("1", "2")));
    }

    @Test
    void findProfilesSkipsHttpForEmptyIds() {
        assertThat(userClient.findProfiles(List.of())).isEmpty();

        assertThat(USER_SERVICE.requests()).isEmpty();
        assertThat(FACILITY_SERVICE.requests()).isEmpty();
    }

    private void assertRequest(String path, Map<String, List<String>> query) {
        assertThat(USER_SERVICE.requests()).containsExactly(new Request("GET", path, query));
        assertThat(FACILITY_SERVICE.requests()).isEmpty();
    }
}
