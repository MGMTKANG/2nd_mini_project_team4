package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;
import com.example.sporty.features.exerciseMatching.repository.MatchParticipantRepository;
import com.example.sporty.features.exerciseMatching.repository.MatchRepository;
import com.example.sporty.features.exerciseMatching.repository.ProcessedEventRepository;
import com.example.sporty.features.exerciseMatching.service.MatchService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:match-user-withdrawn;MODE=MariaDB;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "MATCH_KAFKA_ENABLED=true",
        "USER_WITHDRAWN_TOPIC=match-user-withdrawn-test",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = "match-user-withdrawn-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class UserWithdrawnKafkaIntegrationTest {
    private static final String TOPIC = "match-user-withdrawn-test";
    private static final long WITHDRAWN_USER = 1L;

    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Autowired private UserWithdrawnListener listener;
    @Autowired private MatchRepository matches;
    @Autowired private MatchParticipantRepository participants;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private MatchService matchService;
    @MockitoSpyBean private ProcessedEventRepository processedEvents;

    private TransactionTemplate transactions;
    private DefaultKafkaProducerFactory<String, String> producerFactory;
    private KafkaTemplate<String, String> producer;
    private AdminClient admin;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        producerFactory = new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer());
        producer = new KafkaTemplate<>(producerFactory);
        admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()));
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("userWithdrawnListener"), 1);
    }

    @AfterEach
    void tearDown() {
        producer.destroy();
        producerFactory.destroy();
        admin.close(Duration.ofSeconds(2));
        transactions.executeWithoutResult(status -> {
            participants.deleteAllInBatch();
            matches.deleteAllInBatch();
            processedEvents.deleteAllInBatch();
        });
    }

    @Test
    void withdrawalDeletesOwnedMatchRemovesOnlySelfAndReopensClosedMatch() throws Exception {
        Long owned = createMatch(WITHDRAWN_USER, 3, 2L);
        Long joined = createMatch(2L, 3, WITHDRAWN_USER, 3L);
        Long unrelated = createMatch(3L, 3);
        String eventId = UUID.randomUUID().toString();
        assertThat(matches.findById(joined).orElseThrow().getStatus()).isEqualTo(MatchStatus.CLOSED);

        sendAndAwaitCommit(event(eventId, WITHDRAWN_USER));

        assertThat(matches.existsById(owned)).isFalse();
        assertThat(participants.countByMatch_Id(owned)).isZero();
        assertThat(participants.findAllByMatch_IdOrderByIdAsc(joined))
                .extracting(MatchParticipantEntity::getUserId).containsExactly(2L, 3L);
        assertThat(matches.findById(joined).orElseThrow().getStatus()).isEqualTo(MatchStatus.RECRUITING);
        assertThat(matches.existsById(unrelated)).isTrue();
        assertThat(participants.countByMatch_Id(unrelated)).isEqualTo(1);
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(processedEvents.findById(id(eventId)).orElseThrow().getProcessedAt()).isNotNull();
    }

    @Test
    void duplicateEventIdDoesNotRunCleanupAgain() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Long first = createMatch(WITHDRAWN_USER, 3);
        sendAndAwaitCommit(event(eventId, WITHDRAWN_USER));
        assertThat(matches.existsById(first)).isFalse();
        // 중복 처리 시 재정리를 실행했다면 이 매치가 삭제되어 테스트가 실패한다.
        Long addedAfterProcessing = createMatch(WITHDRAWN_USER, 3);

        sendAndAwaitCommit(event(eventId, WITHDRAWN_USER));

        assertThat(matches.existsById(addedAfterProcessing)).isTrue();
        assertThat(processedEvents.count()).isEqualTo(1);
        verify(matchService, times(1)).removeMatchesForWithdrawal(WITHDRAWN_USER);
    }

    @Test
    void invalidMessagesAreSkippedAndFollowingValidEventIsProcessed() throws Exception {
        Long skippedUserMatch = createMatch(2L, 3);
        Long withdrawnUserMatch = createMatch(WITHDRAWN_USER, 3);
        send("{broken-json");
        send("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"UserWithdrawn\",\"payload\":{}}");
        send(event(UUID.randomUUID().toString(), 2L).replace("UserWithdrawn", "UserUpdated"));
        send(event("not-a-uuid", 2L));
        send(event(UUID.randomUUID().toString(), 2L).replace("\"userId\":2", "\"userId\":\"2\""));

        sendAndAwaitCommit(event(UUID.randomUUID().toString(), WITHDRAWN_USER));

        assertThat(matches.existsById(skippedUserMatch)).isTrue();
        assertThat(matches.existsById(withdrawnUserMatch)).isFalse();
        assertThat(processedEvents.count()).isEqualTo(1);
        verify(matchService, times(1)).removeMatchesForWithdrawal(WITHDRAWN_USER);
    }

    @Test
    void failureRollsBackCleanupAndReceiptBeforeSuccessfulRetry() throws Exception {
        Long owned = createMatch(WITHDRAWN_USER, 3);
        String eventId = UUID.randomUUID().toString();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger inserts = new AtomicInteger();
        AtomicBoolean rollbackObserved = new AtomicBoolean();
        Answer<?> repositoryCalls = mockingDetails(processedEvents).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (inserts.incrementAndGet() == 2) {
                rollbackObserved.set(!processedEvents.existsById(id(eventId)) && matches.existsById(owned));
            }
            return repositoryCalls.answer(invocation);
        }).when(processedEvents).insert(eq(UserWithdrawnHandler.CONSUMER_NAME), eq(eventId), any(LocalDateTime.class));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("test: fail after cleanup");
            }
            return null;
        }).when(matchService).removeMatchesForWithdrawal(WITHDRAWN_USER);

        sendAndAwaitCommit(event(eventId, WITHDRAWN_USER));

        assertThat(rollbackObserved).isTrue();
        assertThat(attempts).hasValue(2);
        assertThat(inserts).hasValue(2);
        assertThat(matches.existsById(owned)).isFalse();
        assertThat(processedEvents.count()).isEqualTo(1);
    }

    @Test
    void exhaustedDatabaseRetriesSkipRecordAndAllowNextEvent() throws Exception {
        Long failedUserMatch = createMatch(2L, 3);
        Long nextUserMatch = createMatch(WITHDRAWN_USER, 3);
        String failedEventId = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            throw new DataAccessResourceFailureException("test: database failure");
        }).when(matchService).removeMatchesForWithdrawal(2L);

        sendAndAwaitCommit(event(failedEventId, 2L));
        assertThat(processedEvents.existsById(id(failedEventId))).isFalse();
        assertThat(matches.existsById(failedUserMatch)).isTrue();
        verify(matchService, times(3)).removeMatchesForWithdrawal(2L);

        sendAndAwaitCommit(event(UUID.randomUUID().toString(), WITHDRAWN_USER));
        assertThat(matches.existsById(nextUserMatch)).isFalse();
        assertThat(processedEvents.count()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateInsertRollsBackLoserAndReturnsNormally() throws Exception {
        Long owned = createMatch(WITHDRAWN_USER, 3);
        String eventId = UUID.randomUUID().toString();
        CountDownLatch bothChecked = new CountDownLatch(2);
        Answer<?> repositoryCalls = mockingDetails(processedEvents).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean exists = (Boolean) repositoryCalls.answer(invocation);
            if (!exists) {
                bothChecked.countDown();
                if (!bothChecked.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test: concurrent receipt check timeout");
                }
            }
            return exists;
        }).when(processedEvents).existsById(id(eventId));

        var executor = Executors.newFixedThreadPool(2);
        try {
            // 동일 파티션의 Kafka 수신은 순차적이므로 경합 경로는 두 호출 스레드로 강제한다.
            var first = executor.submit(() -> listener.onMessage(event(eventId, WITHDRAWN_USER)));
            var second = executor.submit(() -> listener.onMessage(event(eventId, WITHDRAWN_USER)));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(matches.existsById(owned)).isFalse();
        assertThat(processedEvents.count()).isEqualTo(1);
        verify(processedEvents, times(2)).insert(eq(UserWithdrawnHandler.CONSUMER_NAME), eq(eventId), any(LocalDateTime.class));
        verify(matchService, times(1)).removeMatchesForWithdrawal(WITHDRAWN_USER);
    }

    private RecordMetadata send(String message) throws Exception {
        return producer.send(TOPIC, "1", message).get(5, TimeUnit.SECONDS).getRecordMetadata();
    }

    private void sendAndAwaitCommit(String message) throws Exception {
        RecordMetadata record = send(message);
        TopicPartition partition = new TopicPartition(TOPIC, record.partition());
        // DB 반영만 기다리면 중복·무효 이벤트의 소비 완료를 알 수 없어 실제 오프셋도 확인한다.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var offsets = admin.listConsumerGroupOffsets("match-service")
                    .partitionsToOffsetAndMetadata().get(2, TimeUnit.SECONDS);
            assertThat(offsets).containsKey(partition);
            assertThat(offsets.get(partition).offset()).isGreaterThanOrEqualTo(record.offset() + 1);
        });
    }

    private Long createMatch(long owner, int capacity, Long... joinedUsers) {
        return transactions.execute(status -> {
            LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
            MatchEntity match = matches.saveAndFlush(MatchEntity.builder()
                    .title("Kafka 탈퇴 정리").description("이벤트 테스트").serviceId(1L)
                    .startAt(start).endAt(start.plusHours(2)).maxParticipant(capacity)
                    .skillLevel(SkillLevel.BEGINNER).sportType(SportType.FUTSAL)
                    .genderGroup(GenderGroup.MIXED).build());
            participants.save(MatchParticipantEntity.builder().match(match).userId(owner)
                    .role(MatchParticipantRole.OWNER).build());
            for (Long user : joinedUsers) {
                participants.save(MatchParticipantEntity.builder().match(match).userId(user)
                        .role(MatchParticipantRole.PARTICIPANT).build());
            }
            if (joinedUsers.length + 1 == capacity) {
                match.closeRecruitment();
            }
            return match.getId();
        });
    }

    private static ProcessedEventId id(String eventId) {
        return new ProcessedEventId(UserWithdrawnHandler.CONSUMER_NAME, eventId);
    }

    private static String event(String eventId, long userId) {
        return """
                {"eventId":"%s","eventType":"UserWithdrawn","aggregateType":"user",
                 "aggregateId":"%d","occurredAt":"2026-11-02T14:00:00","payload":{"userId":%d}}
                """.formatted(eventId, userId, userId);
    }
}
