package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.support.LocalHttpStub;
import com.example.sporty.support.LocalHttpStub.Request;

import feign.FeignException;

// facility-service FacilityInternalController의 HTTP 계약.
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
        "spring.cloud.openfeign.client.config.default.read-timeout=1500"
})
class FeignFacilityClientHttpTest {

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
    void findServicesUsesRepeatedIdsAndMapsDateTimeAndBooleans() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [
                  {"id":1,"serviceId":"S1","serviceName":"풋살장","locationName":"강남체육관",
                   "region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"},
                  {"id":2,"serviceId":"S2","serviceName":"농구장","locationName":"서초체육관",
                   "region":"서초구","isFree":false,"reservationDeadlineAt":"2026-11-01T18:00:00"}
                ]
                """);

        List<FacilityServiceSummary> services = facilityClient.findServices(List.of("S1", "S2"));

        LocalDateTime deadline = LocalDateTime.of(2026, 11, 1, 18, 0);
        assertThat(services).containsExactly(
                new FacilityServiceSummary(1L, "S1", "풋살장", "강남체육관", "강남구", true, deadline),
                new FacilityServiceSummary(2L, "S2", "농구장", "서초체육관", "서초구", false, deadline));
        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1", "S2")));
    }

    @Test
    void findServicesAllowsNullLocationNameIsFreeAndReservationDeadlineAt() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [{"id":1,"serviceId":"S1","serviceName":"테니스장","locationName":null,
                  "region":"강남구","isFree":null,"reservationDeadlineAt":null}]
                """);

        assertThat(facilityClient.findServices(List.of("S1"))).containsExactly(
                new FacilityServiceSummary(1L, "S1", "테니스장", null, "강남구", null, null));

        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1")));
    }

    @Test
    void findServicesReturnsOnlyExistingServicesWhenSomeIdsAreMissing() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [{"id":1,"serviceId":"S1","serviceName":"풋살장","locationName":"강남체육관",
                  "region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"}]
                """);

        List<FacilityServiceSummary> services = facilityClient.findServices(List.of("S1", "S99"));

        assertThat(services).containsExactly(new FacilityServiceSummary(
                1L, "S1", "풋살장", "강남체육관", "강남구", true, LocalDateTime.of(2026, 11, 1, 18, 0)));
        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1", "S99")));
    }

    @Test
    void findServicesSkipsHttpForEmptyIds() {
        assertThat(facilityClient.findServices(List.of())).isEmpty();
        assertThat(facilityClient.findServicesByIds(List.of())).isEmpty();

        assertThat(FACILITY_SERVICE.requests()).isEmpty();
        assertThat(USER_SERVICE.requests()).isEmpty();
    }

    @Test
    void findServicesMapsEmptyJsonArrayToEmptyList() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, "[]");

        assertThat(facilityClient.findServices(List.of("S1", "S2"))).isEmpty();

        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1", "S2")));
    }

    @Test
    void searchServiceIdsEncodesKoreanRegionAndFreeFilter() {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S1\",\"S2\"]");

        assertThat(facilityClient.searchServiceIds("강남", true)).containsExactly("S1", "S2");

        assertRequest("/internal/facility-services/ids",
                Map.of("region", List.of("강남"), "isFree", List.of("true")));
    }

    @Test
    void searchServiceIdsOmitsNullRegionAndSendsFalseFilter() {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S2\"]");

        assertThat(facilityClient.searchServiceIds(null, false)).containsExactly("S2");

        assertRequest("/internal/facility-services/ids", Map.of("isFree", List.of("false")));
    }

    @Test
    void searchServiceIdsOmitsNullFreeFilter() {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S1\"]");

        assertThat(facilityClient.searchServiceIds("강남", null)).containsExactly("S1");

        assertRequest("/internal/facility-services/ids", Map.of("region", List.of("강남")));
    }

    @Test
    void searchServiceIdsOmitsBothNullFilters() {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, "[\"S1\",\"S2\"]");

        assertThat(facilityClient.searchServiceIds(null, null)).containsExactly("S1", "S2");

        assertRequest("/internal/facility-services/ids", Map.of());
    }

    @Test
    void searchServiceIdsPropagatesServerError() {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 500, """
                {"code":"INTERNAL_SERVER_ERROR","message":"체육시설 서비스 오류"}
                """);

        assertThatThrownBy(() -> facilityClient.searchServiceIds("강남", true))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasCauseInstanceOf(FeignException.InternalServerError.class);

        assertRequest("/internal/facility-services/ids",
                Map.of("region", List.of("강남"), "isFree", List.of("true")));
    }

    @Test
    void findServiceReturnsEmptyWhenRequestedIdIsMissingFromResponse() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [{"id":2,"serviceId":"S2","serviceName":"다른 시설","locationName":null,
                  "region":"서초구","isFree":false,"reservationDeadlineAt":null}]
                """);

        assertThat(facilityClient.findService("S1")).isEmpty();

        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1")));
    }

    @Test
    void findServiceSelectsRequestedIdInsteadOfFirstResponseItem() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [
                  {"id":2,"serviceId":"S2","serviceName":"다른 시설","locationName":null,
                   "region":"서초구","isFree":false,"reservationDeadlineAt":null},
                  {"id":1,"serviceId":"S1","serviceName":"풋살장","locationName":"강남체육관",
                   "region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"}
                ]
                """);

        assertThat(facilityClient.findService("S1")).contains(new FacilityServiceSummary(
                1L, "S1", "풋살장", "강남체육관", "강남구", true, LocalDateTime.of(2026, 11, 1, 18, 0)));

        assertRequest("/internal/facility-services", Map.of("ids", List.of("S1")));
    }

    @Test
    void findServicesByIdsUsesNumericIdsAndMapsBothIdentifiers() {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, """
                [{"id":3000000001,"serviceId":"S240101000000000001","serviceName":"풋살장",
                  "locationName":"강남체육관","region":"강남구","isFree":true,
                  "reservationDeadlineAt":"2026-11-01T18:00:00"}]
                """);

        assertThat(facilityClient.findServicesByIds(List.of(3000000001L, 3000000002L)))
                .containsExactly(new FacilityServiceSummary(3000000001L, "S240101000000000001",
                        "풋살장", "강남체육관", "강남구", true, LocalDateTime.of(2026, 11, 1, 18, 0)));
        assertRequest("/internal/facility-services/by-id", Map.of("ids", List.of("3000000001", "3000000002")));
    }

    @Test
    void findServiceByIdSelectsMatchingNumericIdInsteadOfServiceIdOrFirstItem() {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, """
                [{"id":2,"serviceId":"1"},{"id":1,"serviceId":"S1"}]
                """);

        assertThat(facilityClient.findServiceById(1L)).contains(
                new FacilityServiceSummary(1L, "S1", null, null, null, null, null));
        assertRequest("/internal/facility-services/by-id", Map.of("ids", List.of("1")));
    }

    @Test
    void findServiceByIdReturnsEmptyWhenNumericIdIsMissing() {
        FACILITY_SERVICE.stub("/internal/facility-services/by-id", 200, """
                [{"id":2,"serviceId":"1"}]
                """);

        assertThat(facilityClient.findServiceById(1L)).isEmpty();
        assertRequest("/internal/facility-services/by-id", Map.of("ids", List.of("1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/facility-services", "/internal/facility-services/by-id"})
    void summaryLookupsRejectInvalidResponses(String path) {
        for (String json : List.of("null", "[null]", "[{}]", "[{\"serviceId\":\"S1\"}]",
                "[{\"id\":null,\"serviceId\":\"S1\"}]", "[{\"id\":1}]",
                "[{\"id\":1,\"serviceId\":null}]", "[{\"id\":1,\"serviceId\":\"\"}]",
                "[{\"id\":1,\"serviceId\":\"   \"}]")) {
            FACILITY_SERVICE.reset();
            FACILITY_SERVICE.stub(path, 200, json);

            assertThatThrownBy(() -> lookupSummary(path)).as("%s returned %s", path, json)
                    .isInstanceOf(ExternalServiceUnavailableException.class);
            assertRequest(path, Map.of("ids", List.of(path.endsWith("/by-id") ? "1" : "S1")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/facility-services", "/internal/facility-services/by-id"})
    void summaryLookupsMapFeignErrorsToUnavailable(String path) {
        FACILITY_SERVICE.stub(path, 500, "{}");

        assertThatThrownBy(() -> lookupSummary(path))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasCauseInstanceOf(FeignException.InternalServerError.class);
        assertRequest(path, Map.of("ids", List.of(path.endsWith("/by-id") ? "1" : "S1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[null]", "[\"\"]", "[\"   \"]", "[\"S1\",null]"})
    void searchServiceIdsRejectsInvalidResponses(String json) {
        FACILITY_SERVICE.stub("/internal/facility-services/ids", 200, json);

        assertThatThrownBy(() -> facilityClient.searchServiceIds(null, null))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertRequest("/internal/facility-services/ids", Map.of());
    }

    private List<FacilityServiceSummary> lookupSummary(String path) {
        return path.endsWith("/by-id")
                ? facilityClient.findServicesByIds(List.of(1L)) : facilityClient.findServices(List.of("S1"));
    }

    private void assertRequest(String path, Map<String, List<String>> query) {
        assertThat(FACILITY_SERVICE.requests()).containsExactly(new Request("GET", path, query));
        assertThat(USER_SERVICE.requests()).isEmpty();
    }
}
