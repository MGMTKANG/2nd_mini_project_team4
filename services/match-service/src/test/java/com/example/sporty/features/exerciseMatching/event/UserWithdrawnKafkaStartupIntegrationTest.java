package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-user-withdrawn-startup;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "MATCH_KAFKA_ENABLED=false",
        "USER_WITHDRAWN_TOPIC=match-user-withdrawn-startup-test"
})
@EmbeddedKafka(partitions = 1, topics = "match-user-withdrawn-startup-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class UserWithdrawnKafkaStartupIntegrationTest {
    private static final String TOPIC = "match-user-withdrawn-startup-test";
    private static final String GROUP = "match-startup-" + UUID.randomUUID();

    @DynamicPropertySource
    static void newConsumerGroup(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.consumer.group-id", () -> GROUP);
    }

    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Autowired private ProcessedEventRepository processedEvents;
    @MockitoSpyBean private MatchService matchService;

    @Test
    void newGroupProcessesEventPublishedBeforeListenerStarts() throws Exception {
        var container = listeners.getListenerContainer("userWithdrawnListener");
        assertThat(container).isNotNull();
        assertThat(container.isRunning()).isFalse();
        assertThat(container.getGroupId()).isEqualTo(GROUP);
        String eventId = UUID.randomUUID().toString();
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer());
        var producer = new KafkaTemplate<>(producerFactory);
        try (var admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
            String message = """
                    {"eventId":"%s","eventType":"UserWithdrawn","payload":{"userId":1}}
                    """.formatted(eventId);
            // 발행 완료 뒤 새 그룹을 시작한다. earliest는 운영 설정을 그대로 사용한다.
            var record = producer.send(TOPIC, "1", message).get(5, TimeUnit.SECONDS).getRecordMetadata();
            assertThat(container.isRunning()).isFalse();
            assertThat(processedEvents.count()).isZero();

            container.start();
            ContainerTestUtils.waitForAssignment(container, 1);
            TopicPartition partition = new TopicPartition(TOPIC, record.partition());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(processedEvents.existsById(
                        new ProcessedEventId(UserWithdrawnHandler.CONSUMER_NAME, eventId))).isTrue();
                var offsets = admin.listConsumerGroupOffsets(GROUP)
                        .partitionsToOffsetAndMetadata().get(2, TimeUnit.SECONDS);
                assertThat(offsets).containsKey(partition);
                assertThat(offsets.get(partition).offset()).isEqualTo(record.offset() + 1);
            });
            assertThat(processedEvents.count()).isEqualTo(1);
            verify(matchService, times(1)).removeMatchesForWithdrawal(1L);
        } finally {
            container.stop();
            producer.destroy();
            producerFactory.destroy();
        }
    }
}
