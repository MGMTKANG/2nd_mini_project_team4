package com.example.sporty.features.users.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class UserWithdrawnEventContract {
    private UserWithdrawnEventContract() {
    }

    static JsonNode assertContract(String message, long userId) throws Exception {
        JsonNode envelope = new ObjectMapper().readTree(message);
        assertThat(envelope.isObject()).isTrue();
        assertThat(envelope.size()).isEqualTo(6);
        assertThat(envelope.path("eventId").isTextual()).isTrue();
        String eventId = envelope.path("eventId").textValue();
        // 매치 리스너의 UUID 정규형 검사에 더해 소문자까지 확인한다.
        assertThat(UUID.fromString(eventId).toString()).isEqualTo(eventId);
        assertThat(envelope.path("eventType").isTextual()).isTrue();
        assertThat(envelope.path("eventType").textValue()).isEqualTo("UserWithdrawn");
        assertThat(envelope.path("aggregateType").isTextual()).isTrue();
        assertThat(envelope.path("aggregateType").textValue()).isEqualTo("User");
        assertThat(envelope.path("aggregateId").isTextual()).isTrue();
        assertThat(envelope.path("aggregateId").textValue()).isEqualTo(Long.toString(userId));
        assertThat(envelope.path("occurredAt").isTextual()).isTrue();
        String occurredAt = envelope.path("occurredAt").textValue();
        assertThat(occurredAt).endsWith("Z");
        assertThat(Instant.parse(occurredAt)).isNotNull();
        JsonNode payload = envelope.path("payload");
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.size()).isEqualTo(1);
        // match-service를 import하지 않고 수신 측 양의 long 검사 규칙을 확인한다.
        JsonNode rawUserId = payload.path("userId");
        assertThat(rawUserId.isIntegralNumber()).isTrue();
        assertThat(rawUserId.canConvertToLong()).isTrue();
        assertThat(rawUserId.longValue()).isPositive().isEqualTo(userId);
        return envelope;
    }
}
