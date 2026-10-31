package com.example.sporty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.feign.FeignFacilityClient;
import com.example.sporty.features.exerciseMatching.client.feign.FeignUserClient;

// 다른 서비스 없이도 매치 서비스 전체 설정(Feign 클라이언트 포함)이 뜨는지 확인한다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-service;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "management.endpoint.health.probes.enabled=true"
})
@AutoConfigureMockMvc
class MatchServiceApplicationTests {

    @Autowired
    private UserClient userClient;

    @Autowired
    private FacilityClient facilityClient;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoadsWithFeignClients() {
        assertThat(userClient).isInstanceOf(FeignUserClient.class);
        assertThat(facilityClient).isInstanceOf(FeignFacilityClient.class);
    }

    @Test
    void cloudClientsAreDisabledByDefault() {
        // 이 두 값은 테스트에서 덮어쓰지 않고 application.yml의 기본값을 검증한다.
        assertThat(environment.getProperty("eureka.client.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.cloud.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    void healthIsPublicWithoutLogin() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoIsPublicWithoutLogin() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"liveness", "readiness"})
    void enabledHealthProbesArePublicWithoutLogin(String probe) throws Exception {
        mvc.perform(get("/actuator/health/" + probe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "configprops", "metrics", "loggers"})
    void otherActuatorEndpointsAreNotExposed(String endpoint) throws Exception {
        int anonymousStatus = mvc.perform(get("/actuator/" + endpoint))
                .andReturn().getResponse().getStatus();
        assertThat(anonymousStatus).isIn(401, 403, 404);

        // 인증을 통과해도 404여야 한다. 보안 필터로 가려진 노출도 잡는다.
        mvc.perform(get("/actuator/" + endpoint).with(user("operator")))
                .andExpect(status().isNotFound());
    }
}
