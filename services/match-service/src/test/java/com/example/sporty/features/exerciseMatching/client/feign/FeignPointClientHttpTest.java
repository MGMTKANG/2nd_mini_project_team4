package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.exerciseMatching.InsufficientPointsException;
import com.example.sporty.features.exerciseMatching.client.PointClient;
import com.example.sporty.support.LocalHttpStub;
import com.example.sporty.support.LocalHttpStub.Request;

import feign.FeignException;

// point-service와 실제로 맞춘 HTTP 계약. docs/match-crowdfunding-erd-spec.md 2번 섹션 참고.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-service-point;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.cloud.openfeign.client.config.default.connect-timeout=1000",
        "spring.cloud.openfeign.client.config.default.read-timeout=1500"
})
class FeignPointClientHttpTest {

    private static final LocalHttpStub POINT_SERVICE = new LocalHttpStub();

    @Autowired
    private PointClient pointClient;

    @DynamicPropertySource
    static void clientUrls(DynamicPropertyRegistry registry) {
        registry.add("clients.point-service.url", POINT_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetStubAndVerifyRealClient() {
        assertThat(pointClient).isInstanceOf(FeignPointClient.class);
        POINT_SERVICE.reset();
    }

    @AfterAll
    static void stopStub() {
        POINT_SERVICE.close();
    }

    @Test
    void durableDebitAndCancelSendTheSameOperationId() throws Exception {
        String id = "1bc170cd-b58b-47d9-a6e8-58e7b5585b8e";
        POINT_SERVICE.stub("/internal/points/1/operations/deduct", 200, "{\"balance\":9000}");
        POINT_SERVICE.stub("/internal/points/1/operations/cancel", 200, "{\"balance\":10000}");
        assertThat(pointClient.deduct(1L, 1000L, id)).isEqualTo(9000L);
        Request debit = POINT_SERVICE.requests().get(0);
        assertThat(debit.path()).isEqualTo("/internal/points/1/operations/deduct");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(POINT_SERVICE.requestBodies().get(0)).path("operationId").asText()).isEqualTo(id);
        assertThat(pointClient.cancel(1L, 1000L, id)).isEqualTo(10000L);
        Request cancel = POINT_SERVICE.requests().get(1);
        assertThat(cancel.path()).isEqualTo("/internal/points/1/operations/cancel");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(POINT_SERVICE.requestBodies().get(1)).path("operationId").asText()).isEqualTo(id);
    }

    @Test
    void operationConflictIsNotMisreportedAsInsufficientPoints() {
        POINT_SERVICE.stub("/internal/points/1/operations/deduct", 409,
                "{\"code\":\"POINT_OPERATION_CANCELLED\"}");
        assertThatThrownBy(() -> pointClient.deduct(1L, 1000L, "1bc170cd-b58b-47d9-a6e8-58e7b5585b8e"))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void oldPointServerCannotSilentlyApplyAnUntrackedDebit() {
        // Old server has only the legacy route; the operation route is unavailable.
        POINT_SERVICE.stub("/internal/points/1/deduct", 200, "{\"balance\":9000}");
        assertThatThrownBy(() -> pointClient.deduct(1L, 1000L, "1bc170cd-b58b-47d9-a6e8-58e7b5585b8e"))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(POINT_SERVICE.requests()).extracting(Request::path)
                .containsExactly("/internal/points/1/operations/deduct");
    }

    @Test
    void legacyContributionRefundStillUsesTheIdempotentRoute() throws Exception {
        String id = "1bc170cd-b58b-47d9-a6e8-58e7b5585b8e";
        POINT_SERVICE.stub("/internal/points/1/operations/refund", 200, "{\"balance\":10000}");
        assertThat(pointClient.refund(1L, 1000L, id)).isEqualTo(10000L);
        assertThat(POINT_SERVICE.requests()).extracting(Request::path)
                .containsExactly("/internal/points/1/operations/refund");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(POINT_SERVICE.requestBodies().get(0))
                .path("operationId").asText()).isEqualTo(id);
    }

    @Test
    void deductReturnsUpdatedBalance() {
        POINT_SERVICE.stub("/internal/points/1/deduct", 200, """
                {"balance":9000}
                """);

        Long balance = pointClient.deduct(1L, 1000L);

        assertThat(balance).isEqualTo(9000L);
        assertRequest("/internal/points/1/deduct");
    }

    @Test
    void deductThrowsInsufficientPointsOn409() {
        POINT_SERVICE.stub("/internal/points/1/deduct", 409, """
                {"code":"INSUFFICIENT_POINTS","message":"포인트가 부족합니다."}
                """);

        assertThatThrownBy(() -> pointClient.deduct(1L, 999999L))
                .isInstanceOf(InsufficientPointsException.class);
        assertRequest("/internal/points/1/deduct");
    }

    @Test
    void deductPropagatesServerErrorAsExternalServiceUnavailable() {
        POINT_SERVICE.stub("/internal/points/1/deduct", 500, """
                {"code":"INTERNAL_ERROR","message":"point-service 오류"}
                """);

        assertThatThrownBy(() -> pointClient.deduct(1L, 1000L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasCauseInstanceOf(FeignException.InternalServerError.class);
        assertRequest("/internal/points/1/deduct");
    }

    @Test
    void refundReturnsUpdatedBalance() {
        POINT_SERVICE.stub("/internal/points/1/refund", 200, """
                {"balance":10000}
                """);

        Long balance = pointClient.refund(1L, 1000L);

        assertThat(balance).isEqualTo(10000L);
        assertRequest("/internal/points/1/refund");
    }

    @Test
    void refundPropagatesServerErrorAsExternalServiceUnavailable() {
        POINT_SERVICE.stub("/internal/points/1/refund", 500, """
                {"code":"INTERNAL_ERROR","message":"point-service 오류"}
                """);

        assertThatThrownBy(() -> pointClient.refund(1L, 1000L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasCauseInstanceOf(FeignException.InternalServerError.class);
        assertRequest("/internal/points/1/refund");
    }

    private void assertRequest(String path) {
        assertThat(POINT_SERVICE.requests()).containsExactly(new Request("POST", path, Map.of()));
    }
}
