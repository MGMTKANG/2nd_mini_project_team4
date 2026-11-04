package com.example.sporty.features.users.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserWithdrawnEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserWithdrawnKafkaProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserWithdrawnEvent event) {
        if (!properties.isKafkaEnabled()) {
            log.debug("회원 탈퇴 Kafka 발행 비활성화: eventId={}", event.getEventId());
            return;
        }

        try {
            String aggregateId = event.getUserId().toString();
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.getEventId().toString());
            envelope.put("eventType", "UserWithdrawn");
            envelope.put("aggregateType", "User");
            envelope.put("aggregateId", aggregateId);
            envelope.put("occurredAt", event.getOccurredAt().toString());
            envelope.putObject("payload").put("userId", event.getUserId().longValue());

            // 전송 완료를 기다리지 않고 결과만 기록한다.
            kafkaTemplate.send(properties.getUserWithdrawnTopic(), aggregateId,
                    objectMapper.writeValueAsString(envelope)).whenComplete((result, exception) -> {
                        if (exception != null) {
                            logFailure(event, exception);
                        } else {
                            var metadata = result.getRecordMetadata();
                            log.info("회원 탈퇴 이벤트 발행 완료: topic={}, partition={}, offset={}, eventId={}",
                                    metadata.topic(), metadata.partition(), metadata.offset(), event.getEventId());
                        }
                    });
        } catch (Exception exception) {
            // 커밋 후 발행 실패는 탈퇴 응답에 전파하지 않는다.
            logFailure(event, exception);
        }
    }

    private void logFailure(UserWithdrawnEvent event, Throwable exception) {
        // 예외 메시지와 이벤트 원문에는 개인 정보가 포함될 수 있다.
        log.error("회원 탈퇴 이벤트 발행 실패: eventId={}, error={}",
                event.getEventId(), exception.getClass().getSimpleName());
    }
}
