package com.example.sporty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "prod"})
    void profilesKeepCloudClientsDisabledAndOnlyLocalShowsSql(String profile) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class))
                    .isEqualTo("local".equals(profile));
            assertThat(environment.getProperty("eureka.client.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.cloud.config.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.cloud.config.request-connect-timeout", Integer.class)).isEqualTo(2000);
            assertThat(environment.getProperty("spring.cloud.config.request-read-timeout", Integer.class)).isEqualTo(5000);
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        });
    }

    @Test
    void productionDdlModeCanBeOverriddenToValidate() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "MATCH_JPA_DDL_AUTO=validate")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("validate");
                });
    }
}
