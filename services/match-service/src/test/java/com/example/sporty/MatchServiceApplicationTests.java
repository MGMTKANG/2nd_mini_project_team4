package com.example.sporty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes"
})
class MatchServiceApplicationTests {

    @Autowired
    private UserClient userClient;

    @Autowired
    private FacilityClient facilityClient;

    @Test
    void contextLoadsWithFeignClients() {
        assertThat(userClient).isInstanceOf(FeignUserClient.class);
        assertThat(facilityClient).isInstanceOf(FeignFacilityClient.class);
    }
}
