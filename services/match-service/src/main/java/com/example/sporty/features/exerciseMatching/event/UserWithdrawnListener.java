package com.example.sporty.features.exerciseMatching.event;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserWithdrawnListener {
    private final ObjectMapper objectMapper;
    private final UserWithdrawnHandler handler;

    @KafkaListener(id = "userWithdrawnListener", idIsGroup = false,
            topics = "${match.events.user-withdrawn-topic}")
    public void onMessage(String message) {
        String eventId;
        long userId;
        try {
            JsonNode envelope = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(message);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("envelope must be an object");
            }
            if (!"UserWithdrawn".equals(envelope.path("eventType").asText())) {
                log.info("회원 탈퇴 외 이벤트 건너뜀");
                return;
            }
            String rawEventId = envelope.path("eventId").asText();
            eventId = UUID.fromString(rawEventId).toString();
            if (!eventId.equalsIgnoreCase(rawEventId)) {
                throw new IllegalArgumentException("eventId must be a UUID");
            }
            JsonNode rawUserId = envelope.path("payload").path("userId");
            if (!rawUserId.isIntegralNumber() || !rawUserId.canConvertToLong() || rawUserId.longValue() <= 0) {
                throw new IllegalArgumentException("payload.userId must be a positive long");
            }
            userId = rawUserId.longValue();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // 원문에는 개인 정보가 포함될 수 있으므로 로그에 남기지 않는다.
            log.warn("잘못된 회원 탈퇴 이벤트 건너뜀 ({})", exception.getClass().getSimpleName());
            return;
        }

        try {
            handler.handle(eventId, userId);
        } catch (UserWithdrawnHandler.EventClaimException exception) {
            // handle의 트랜잭션 롤백이 끝난 뒤 조회한다. PK 충돌 외 DB 오류는 재시도한다.
            if (!handler.wasProcessed(eventId)) {
                throw exception.getCause();
            }
            log.info("이미 처리된 회원 탈퇴 이벤트 건너뜀: eventId={}", eventId);
        }
        // DB 커밋(또는 중복 확인) 후 정상 반환하면 컨테이너가 오프셋을 확정한다.
    }
}
