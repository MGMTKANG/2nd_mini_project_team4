package com.example.sporty.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 MariaDB를 사용하는 저장소/서비스 테스트의 공통 설정. H2로 대체하지 않는다. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
        "spring.datasource.hikari.maximum-pool-size=6",
        "spring.datasource.hikari.connection-timeout=10000",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=20",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
public abstract class MariaDBIntegrationTestSupport {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        MariaDBContainer<?> database = SharedDatabase.INSTANCE;
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.datasource.driver-class-name", database::getDriverClassName);
    }

    private static final class SharedDatabase {
        // Docker 사용 가능 조건을 통과하고 Spring 컨텍스트가 생성될 때만 시작한다.
        // @Container의 클래스별 stop을 사용하지 않아 같은 JVM의 후속 클래스에서도 재사용한다.
        // JVM 종료 시 Testcontainers의 리소스 정리기가 제거한다(withReuse는 사용하지 않음).
        private static final MariaDBContainer<?> INSTANCE = startDatabase();

        private static MariaDBContainer<?> startDatabase() {
            MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11.4.8")
                    .withDatabaseName("match_test")
                    .withUsername("match_test")
                    .withPassword("match_test");
            database.start();
            return database;
        }
    }
}
