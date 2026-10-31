package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.Environment;

// DB/JWT만 테스트 설정으로 교체하며 Feign 타임아웃은 application.yml 값을 그대로 읽는다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-timeout-configuration;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes"
})
class FeignTimeoutConfigurationTest {

    @Autowired private FeignClientProperties properties;
    @Autowired private Environment environment;

    @Test
    void loadsProductionDefaultTimeoutsWithoutTestOverrides() {
        assertThat(properties.getDefaultConfig()).isEqualTo("default");
        FeignClientProperties.FeignClientConfiguration defaults = properties.getConfig().get("default");
        assertThat(defaults).isNotNull();
        assertThat(defaults.getConnectTimeout()).isEqualTo(1000);
        assertThat(defaults.getReadTimeout()).isEqualTo(3000);
        assertThat(environment.getProperty(
                "spring.cloud.openfeign.client.config.default.connect-timeout", Integer.class)).isEqualTo(1000);
        assertThat(environment.getProperty(
                "spring.cloud.openfeign.client.config.default.read-timeout", Integer.class)).isEqualTo(3000);
    }
}
