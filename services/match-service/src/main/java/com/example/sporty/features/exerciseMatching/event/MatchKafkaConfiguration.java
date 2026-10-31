package com.example.sporty.features.exerciseMatching.event;

import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.Assert;
import org.springframework.util.backoff.ExponentialBackOff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class MatchKafkaConfiguration {
    @Bean
    DefaultErrorHandler userWithdrawnErrorHandler(ObjectMapper objectMapper,
            @Value("${match.kafka.retry.initial-interval-ms}") long initialInterval,
            @Value("${match.kafka.retry.max-interval-ms}") long maxInterval) {
        Assert.isTrue(initialInterval > 0 && maxInterval >= initialInterval,
                "Kafka retry intervals must satisfy 0 < initial <= max");
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
        backOff.setMaxInterval(maxInterval);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) ->
                log.error("재시도 불가 회원 탈퇴 이벤트 건너뜀: topic={}, partition={}, offset={}, eventId={}",
                        record.topic(), record.partition(), record.offset(), eventId(record, objectMapper), exception),
                backOff);
        // DB/연결 오류는 계속 재시도한다. 형식 오류와 명백한 프로그래밍 오류만 즉시 건너뛴다.
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class,
                NullPointerException.class, ClassCastException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("회원 탈퇴 이벤트 처리 실패: topic={}, partition={}, offset={}, eventId={}, attempt={}",
                        record.topic(), record.partition(), record.offset(), eventId(record, objectMapper),
                        deliveryAttempt));
        return errorHandler;
    }

    private String eventId(ConsumerRecord<?, ?> record, ObjectMapper objectMapper) {
        if (record.value() instanceof String message) {
            try {
                JsonNode envelope = objectMapper.readTree(message);
                if (envelope != null) {
                    return UUID.fromString(envelope.path("eventId").asText()).toString();
                }
            } catch (JsonProcessingException | IllegalArgumentException ignored) {
                // 원문이나 유효하지 않은 ID를 로그로 노출하지 않는다.
            }
        }
        return "unknown";
    }
}
