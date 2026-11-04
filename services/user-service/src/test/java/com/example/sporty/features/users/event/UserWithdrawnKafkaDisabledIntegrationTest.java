package com.example.sporty.features.users.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.UserService;

// 활성화 플래그를 덮어쓰지 않고 브로커 없는 기본 실행을 확인한다.
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:user-withdrawn-disabled;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
@DirtiesContext
@MockitoSpyBean(types = KafkaTemplate.class, name = "kafkaTemplate")
@MockitoSpyBean(types = ProducerFactory.class, name = "kafkaProducerFactory")
class UserWithdrawnKafkaDisabledIntegrationTest {
    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private UserWithdrawnKafkaProperties properties;
    @Autowired private KafkaProperties kafkaProperties;
    @Autowired private Environment environment;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ProducerFactory<String, String> producerFactory;
    @Autowired private ProducerListener<Object, Object> producerListener;
    @MockitoBean private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("기본 설정으로 Kafka 연결 없이 앱이 뜨고 탈퇴가 커밋된다")
    void defaultsDisableKafkaAndDoNotCreateProducer() {
        assertThat(properties.isKafkaEnabled()).isFalse();
        assertThat(properties.getUserWithdrawnTopic()).isEqualTo("user.withdrawn");
        assertThat(environment.getProperty("spring.kafka.admin.auto-create", Boolean.class)).isFalse();
        assertThat(kafkaProperties.getProducer().getProperties()).containsEntry("max.block.ms", "3000");
        Long userId = users.saveAndFlush(UserEntity.builder()
                .email("disabled@example.test").password("encoded-password").gender(Gender.MALE).build()).getId();
        clearInvocations(kafkaTemplate, producerFactory);

        assertThatCode(() -> userService.withdraw(userId)).doesNotThrowAnyException();

        verifyNoInteractions(kafkaTemplate, producerFactory);
        assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("Kafka 기본 실패 리스너가 메시지 원문과 예외 상세를 기록하지 않는다")
    @ExtendWith(OutputCaptureExtension.class)
    void producerListenerDoesNotLogContents(CapturedOutput output) {
        String privateMessage = "private-email@example.test";

        producerListener.onError(new ProducerRecord<>("user.withdrawn", "private-key", privateMessage),
                null, new IllegalStateException("private-exception-detail"));

        assertThat(output.getAll()).doesNotContain(privateMessage, "private-key", "private-exception-detail");
    }
}
