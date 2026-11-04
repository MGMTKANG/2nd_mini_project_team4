package com.example.sporty.features.users.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.UserService;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:user-withdrawn;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "USER_KAFKA_ENABLED=true",
        "USER_WITHDRAWN_TOPIC=user-withdrawn-test"
})
@EmbeddedKafka(partitions = 1, topics = "user-withdrawn-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@MockitoSpyBean(types = KafkaTemplate.class, name = "kafkaTemplate")
class UserWithdrawnKafkaIntegrationTest {
    private static final String TOPIC = "user-withdrawn-test";

    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private RefreshTokenService refreshTokenService;

    private Consumer<String, String> consumer;
    private Long userId;

    @BeforeEach
    void setUp() {
        consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker),
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, true, TOPIC);
        users.deleteAll();
        userId = users.saveAndFlush(UserEntity.builder()
                .email("withdrawal@example.test").password("encoded-password").gender(Gender.FEMALE).build()).getId();
    }

    @AfterEach
    void tearDown() {
        consumer.close(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("실제 탈퇴 트랜잭션 커밋 뒤 토픽에 계약에 맞는 키와 값이 들어간다")
    void withdrawalPublishesToKafka() throws Exception {
        userService.withdraw(userId);

        assertPublishedRecord();
        assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
    }

    private void assertPublishedRecord() throws Exception {
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
        assertThat(record.key()).isEqualTo(userId.toString());
        UserWithdrawnEventContract.assertContract(record.value(), userId);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), eq(record.value()));
    }
}
