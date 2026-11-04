package com.example.sporty.features.users.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.auth.service.RefreshTokenService;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.users.service.UserService;

// 실제 H2 트랜잭션과 모의 Kafka로 커밋 경계 및 응답 실패 격리를 확인한다.
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:user-withdrawn-transaction;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "USER_KAFKA_ENABLED=true",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
@DirtiesContext
@MockitoBean(types = KafkaTemplate.class, name = "kafkaTemplate")
class UserWithdrawnTransactionIntegrationTest {
    private static final String TOPIC = "user.withdrawn";

    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private RefreshTokenService refreshTokenService;

    private TransactionTemplate transactions;
    private Long userId;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        users.deleteAll();
        userId = users.saveAndFlush(UserEntity.builder()
                .email("transaction@example.test").password("encoded-password").gender(Gender.FEMALE).build()).getId();
        clearInvocations((Object) kafkaTemplate);
    }

    @Test
    @DisplayName("외부 트랜잭션 커밋 뒤 정확히 한 번 발행하고 이벤트 계약을 지킨다")
    void publishesOnlyAfterCommit() throws Exception {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(new ProducerRecord<>(TOPIC, "key", "value"), metadata)));

        transactions.executeWithoutResult(status -> {
            userService.withdraw(userId);
            verifyNoInteractions(kafkaTemplate);
        });

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), value.capture());
        verifyNoMoreInteractions(kafkaTemplate);
        UserWithdrawnEventContract.assertContract(value.getValue(), userId);
        assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("롤백되면 보내지 않으며 DB 회원 상태가 복구된다")
    void rollbackDoesNotPublish() {
        transactions.executeWithoutResult(status -> {
            userService.withdraw(userId);
            status.setRollbackOnly();
        });

        verifyNoInteractions(kafkaTemplate);
        assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
    }

    @ParameterizedTest(name = "동기 실패={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("send 예외 또는 실패한 future가 있어도 탈퇴 호출은 정상 반환하고 DB에 커밋된다")
    void producerFailureDoesNotFailWithdrawal(boolean synchronousFailure) {
        TimeoutException failure = new TimeoutException("test-only failure");
        if (synchronousFailure) {
            when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(failure);
        } else {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(failure));
        }

        assertThatCode(() -> userService.withdraw(userId)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), anyString());
        assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 등록된 이벤트는 발행하지 않는다")
    void eventWithoutTransactionDoesNotPublish() {
        events.publishEvent(new UserWithdrawnEvent(userId, UUID.randomUUID(), Instant.now()));

        verifyNoInteractions(kafkaTemplate);
    }
}
