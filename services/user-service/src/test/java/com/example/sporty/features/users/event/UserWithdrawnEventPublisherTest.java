package com.example.sporty.features.users.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class UserWithdrawnEventPublisherTest {
    private static final String TOPIC = "custom.user.withdrawn";
    private static final long USER_ID = Long.MAX_VALUE;
    private static final String PRIVATE_MESSAGE = "private-email@example.test";

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserWithdrawnKafkaProperties properties;
    private UserWithdrawnEventPublisher publisher;
    private UserWithdrawnEvent event;

    @BeforeEach
    void setUp() {
        properties = new UserWithdrawnKafkaProperties();
        properties.setKafkaEnabled(true);
        properties.setUserWithdrawnTopic(TOPIC);
        publisher = new UserWithdrawnEventPublisher(kafkaTemplate, objectMapper, properties);
        event = new UserWithdrawnEvent(USER_ID, UUID.randomUUID(), Instant.parse("2026-11-04T12:34:56.789Z"));
    }

    @Test
    @DisplayName("활성화 시 정확한 토픽·키로 한 번 보내고 매치 수신 계약과 성공 로그를 만족한다")
    void sendsContractOnceAndLogsMetadata(CapturedOutput output) throws Exception {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 2), 42, 0, 0, 0, 0);
        SendResult<String, String> result = new SendResult<>(new ProducerRecord<>(TOPIC, "key", "value"), metadata);
        when(kafkaTemplate.send(eq(TOPIC), eq(Long.toString(USER_ID)), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.publish(event);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(Long.toString(USER_ID)), value.capture());
        verifyNoMoreInteractions(kafkaTemplate);
        var envelope = UserWithdrawnEventContract.assertContract(value.getValue(), USER_ID);
        assertThat(envelope.path("eventId").textValue()).isEqualTo(event.getEventId().toString());
        assertThat(Instant.parse(envelope.path("occurredAt").textValue())).isEqualTo(event.getOccurredAt());
        assertThat(output.getOut()).contains("topic=" + TOPIC, "partition=2", "offset=42",
                "eventId=" + event.getEventId()).doesNotContain(value.getValue());
    }

    @Test
    @DisplayName("비활성화 시 KafkaTemplate을 호출하지 않는다")
    void disabledDoesNotTouchKafka() {
        properties.setKafkaEnabled(false);

        publisher.publish(event);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("send 동기 예외를 삼키고 예외 메시지 없이 실패를 기록한다")
    void synchronousFailureIsIsolated(CapturedOutput output) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new TimeoutException(PRIVATE_MESSAGE));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertFailureLog(output, "TimeoutException");
    }

    @Test
    @DisplayName("실패한 future도 탈퇴 호출에 예외를 전파하지 않는다")
    void failedFutureIsIsolated(CapturedOutput output) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException(PRIVATE_MESSAGE)));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertFailureLog(output, "IllegalStateException");
    }

    @Test
    @DisplayName("전송 결과를 기다리지 않으며 뒤늦은 실패도 로그로만 처리한다")
    void pendingSendReturnsBeforeCompletion(CapturedOutput output) {
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(pending);

        publisher.publish(event);

        assertThat(pending).isNotDone();
        assertThatCode(() -> pending.completeExceptionally(new TimeoutException(PRIVATE_MESSAGE)))
                .doesNotThrowAnyException();
        assertFailureLog(output, "TimeoutException");
    }

    @Test
    @DisplayName("JSON 직렬화 실패도 응답에 전파하지 않으며 Kafka를 호출하지 않는다")
    void serializationFailureIsIsolated(CapturedOutput output) throws Exception {
        ObjectMapper failingMapper = spy(new ObjectMapper());
        doThrow(new JsonProcessingException(PRIVATE_MESSAGE) { })
                .when(failingMapper).writeValueAsString(org.mockito.ArgumentMatchers.any(ObjectNode.class));
        publisher = new UserWithdrawnEventPublisher(kafkaTemplate, failingMapper, properties);

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        verifyNoInteractions(kafkaTemplate);
        assertThat(output.getAll()).contains("eventId=" + event.getEventId())
                .doesNotContain(PRIVATE_MESSAGE, "payload", "aggregateId");
    }

    private void assertFailureLog(CapturedOutput output, String exceptionClass) {
        assertThat(output.getAll()).contains("eventId=" + event.getEventId(), "error=" + exceptionClass)
                .doesNotContain(PRIVATE_MESSAGE, "payload", "aggregateId");
    }
}
