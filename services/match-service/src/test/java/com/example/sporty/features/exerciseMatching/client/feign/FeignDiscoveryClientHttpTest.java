package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;
import com.example.sporty.support.LocalHttpStub;
import com.example.sporty.support.LocalHttpStub.Request;

import feign.Client;

// Eureka 서버 없이도 URL을 비운 실제 Feign 클라이언트가 서비스 이름으로 호출하는지 검증한다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-discovery-client;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FeignDiscoveryClientHttpTest {

    private static final LocalHttpStub USER_SERVICE = new LocalHttpStub();
    private static final LocalHttpStub FACILITY_SERVICE = new LocalHttpStub();

    @Autowired private UserClient userClient;
    @Autowired private FacilityClient facilityClient;
    @Autowired private SimpleDiscoveryClient discoveryClient;
    @Autowired private FeignClientFactory feignClientFactory;
    @Autowired private Environment environment;

    @DynamicPropertySource
    static void discoveryInstances(DynamicPropertyRegistry registry) {
        registry.add("clients.user-service.url", () -> "");
        registry.add("clients.facility-service.url", () -> "");
        registry.add("spring.cloud.discovery.client.simple.instances.user-service[0].uri",
                USER_SERVICE::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.facility-service[0].uri",
                FACILITY_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetStubsAndVerifyDiscoveryMode() {
        USER_SERVICE.reset();
        FACILITY_SERVICE.reset();
        assertThat(userClient).isInstanceOf(FeignUserClient.class);
        assertThat(facilityClient).isInstanceOf(FeignFacilityClient.class);
        assertThat(environment.getProperty("clients.user-service.url")).isEmpty();
        assertThat(environment.getProperty("clients.facility-service.url")).isEmpty();
    }

    @AfterAll
    static void stopStubs() {
        USER_SERVICE.close();
        FACILITY_SERVICE.close();
    }

    @Test
    void userClientReachesStubThroughServiceNameAndLoadBalancer() {
        USER_SERVICE.stub("/internal/users/1", 200, """
                {"userId":1,"status":"ACTIVE"}
                """);
        assertDiscoveryAndLoadBalancer("user-service", USER_SERVICE);

        assertThat(userClient.findUser(1L)).contains(new UserSummary(1L, "ACTIVE"));

        assertThat(USER_SERVICE.requests())
                .containsExactly(new Request("GET", "/internal/users/1", Map.of()));
        assertThat(FACILITY_SERVICE.requests()).isEmpty();
    }

    @Test
    void facilityClientReachesStubThroughServiceNameAndLoadBalancer() {
        FACILITY_SERVICE.stub("/internal/facility-services", 200, """
                [{"serviceId":7,"serviceName":"풋살장","locationName":"체육관",
                  "region":"강남구","isFree":true,"reservationDeadlineAt":null}]
                """);
        assertDiscoveryAndLoadBalancer("facility-service", FACILITY_SERVICE);

        assertThat(facilityClient.findServices(List.of(7L))).containsExactly(
                new FacilityServiceSummary(7L, "풋살장", "체육관", "강남구", true, null));

        assertThat(FACILITY_SERVICE.requests()).containsExactly(
                new Request("GET", "/internal/facility-services", Map.of("ids", List.of("7"))));
        assertThat(USER_SERVICE.requests()).isEmpty();
    }

    private void assertDiscoveryAndLoadBalancer(String serviceId, LocalHttpStub stub) {
        assertThat(discoveryClient.getInstances(serviceId))
                .extracting(instance -> instance.getUri().toString()).containsExactly(stub.baseUrl());
        assertThat(feignClientFactory.getInstance(serviceId, Client.class))
                .isInstanceOf(FeignBlockingLoadBalancerClient.class);
    }
}
