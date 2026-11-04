package com.example.sporty.features.points.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.example.sporty.features.commons.exception.points.*;
import com.example.sporty.features.points.repository.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
        "spring.datasource.hikari.maximum-pool-size=12",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PointService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class PointIdempotencyMariaDBTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        MariaDBContainer<?> database = SharedDatabase.INSTANCE;
        r.add("spring.datasource.url", database::getJdbcUrl);
        r.add("spring.datasource.username", database::getUsername);
        r.add("spring.datasource.password", database::getPassword);
        r.add("spring.datasource.driver-class-name", database::getDriverClassName);
    }

    // Lazy start avoids connecting when Docker tests are skipped. Ryuk removes this disposable DB
    // after the JVM closes its Spring context, so Hibernate never races a stopped container.
    private static final class SharedDatabase {
        static final MariaDBContainer<?> INSTANCE = start();
        static MariaDBContainer<?> start() {
            var database = new MariaDBContainer<>("mariadb:11.4.8")
                    .withDatabaseName("point_test").withUsername("point_test").withPassword("point_test");
            database.start();
            try (var connection = java.sql.DriverManager.getConnection(database.getJdbcUrl(),
                    database.getUsername(), database.getPassword()); var sql = connection.createStatement()) {
                sql.execute("""
                        CREATE TABLE point_wallet (
                            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                            user_id BIGINT NOT NULL UNIQUE,
                            version BIGINT NOT NULL,
                            balance BIGINT NOT NULL,
                            created_at DATETIME(6) NOT NULL,
                            updated_at DATETIME(6) NOT NULL
                        ) ENGINE=InnoDB
                        """);
                String migration = java.nio.file.Files.readString(
                        java.nio.file.Path.of("docs/sql/2026-11-05-point-idempotency.sql"));
                sql.execute(migration);
                sql.execute(migration); // reviewed migration is re-runnable without resetting records
                return database;
            } catch (Exception exception) {
                database.stop();
                throw new IllegalStateException("Local test schema migration failed", exception);
            }
        }
    }
    @Autowired PointService service;
    @Autowired PointAccountRepository accounts;
    @Autowired PointOperationRepository operations;
    @Autowired PlatformTransactionManager transactionManager;
    @BeforeEach void clear() { operations.deleteAllInBatch(); accounts.deleteAllInBatch(); }

    @Test void lostResponsesCanBeRetriedWithoutRepeatingDebitOrLegacyRefund() {
        String debit = id();
        assertThat(service.deduct(1L, 2000L, debit)).isEqualTo(8000L);
        assertThat(service.deduct(1L, 2000L, debit)).isEqualTo(8000L);
        String refund = id();
        assertThat(service.refund(1L, 2000L, refund)).isEqualTo(10000L);
        assertThat(service.refund(1L, 2000L, refund)).isEqualTo(10000L);
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
        assertThat(operations.count()).isEqualTo(2);
    }
    @Test void cancelAfterCommittedDebitCompensatesOnlyOnce() {
        String op = id(); service.deduct(1L, 2000L, op);
        assertThat(service.cancel(1L, 2000L, op)).isEqualTo(10000L);
        assertThat(service.cancel(1L, 2000L, op)).isEqualTo(10000L);
        assertThatThrownBy(() -> service.deduct(1L, 2000L, op)).isInstanceOf(PointOperationCancelledException.class);
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
    }
    @Test void cancelBeforeDelayedDebitDoesNotGiveFreePoints() {
        String op = id();
        assertThat(service.cancel(1L, 2000L, op)).isEqualTo(10000L);
        assertThatThrownBy(() -> service.deduct(1L, 2000L, op)).isInstanceOf(PointOperationCancelledException.class);
        assertThat(service.cancel(1L, 2000L, op)).isEqualTo(10000L);
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
    }
    @Test void sameKeyCannotBeReboundToAnotherUserAmountOrAction() {
        String op = id(); service.deduct(1L, 2000L, op);
        assertThatThrownBy(() -> service.deduct(2L, 2000L, op)).isInstanceOf(PointOperationConflictException.class);
        assertThatThrownBy(() -> service.deduct(1L, 3000L, op)).isInstanceOf(PointOperationConflictException.class);
        assertThatThrownBy(() -> service.cancel(1L, 3000L, op)).isInstanceOf(PointOperationConflictException.class);
        assertThatThrownBy(() -> service.refund(1L, 2000L, op)).isInstanceOf(PointOperationConflictException.class);
        assertThat(service.getBalance(1L)).isEqualTo(8000L);
        assertThat(accounts.count()).isOne();
    }
    @Test void insufficientDebitCanBeCancelledWithoutCreditingIt() {
        String op = id();
        assertThatThrownBy(() -> service.deduct(1L, 20000L, op)).isInstanceOf(InsufficientPointsException.class);
        service.cancel(1L, 20000L, op);
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
        assertThatThrownBy(() -> service.deduct(1L, 20000L, op)).isInstanceOf(PointOperationCancelledException.class);
    }
    @Test void transactionRollbackRevertsWalletAndOperationTogether() {
        String op = id();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.deduct(1L, 2000L, op);
            throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(operations.existsById(op)).isFalse();
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
        service.deduct(1L, 2000L, op);
        assertThat(service.getBalance(1L)).isEqualTo(8000L);
    }
    @Test void concurrentFirstRequestsWithSameKeyChargeOnce() throws Exception {
        String op = id();
        assertThat(parallel(8, () -> service.deduct(1L, 1000L, op))).containsOnly(9000L);
        assertThat(accounts.count()).isOne(); assertThat(operations.count()).isOne();
        assertThat(service.getBalance(1L)).isEqualTo(9000L);
    }
    @Test void concurrentFirstWalletCreationPreservesEveryIndependentDebit() throws Exception {
        parallel(8, () -> service.deduct(1L, 500L, id()));
        assertThat(accounts.count()).isOne(); assertThat(operations.count()).isEqualTo(8);
        assertThat(service.getBalance(1L)).isEqualTo(6000L);
    }
    @Test void racingCancelAndDebitEndCancelledWithoutLosingBalance() throws Exception {
        String op = id(); AtomicInteger n = new AtomicInteger();
        parallel(8, () -> {
            if (n.getAndIncrement() % 2 == 0) return service.cancel(1L, 2000L, op);
            try { return service.deduct(1L, 2000L, op); }
            catch (PointOperationCancelledException expected) { return 10000L; }
        });
        assertThat(service.getBalance(1L)).isEqualTo(10000L);
        assertThat(operations.findById(op).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }
    @Test void concurrentLegacyRefundRetriesCreditOnce() throws Exception {
        String op = id(); parallel(8, () -> service.refund(1L, 2000L, op));
        assertThat(service.getBalance(1L)).isEqualTo(12000L);
        assertThat(operations.count()).isOne();
    }
    static String id() { return UUID.randomUUID().toString(); }
    static <T> List<T> parallel(int count, Callable<T> task) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return task.call();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
