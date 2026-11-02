package com.example.sporty.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

class MatchDatabaseCleanupTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        transactionManager = new DataSourceTransactionManager(database);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void clearsAllFourTablesInForeignKeyOrderDespiteCallerRollback() {
        createMatchTables();
        jdbc.execute("CREATE TABLE processed_event (event_id VARCHAR(36) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE match_user_state (user_id BIGINT PRIMARY KEY, withdrawn BOOLEAN)");
        jdbc.update("INSERT INTO processed_event VALUES ('event-1')");
        jdbc.update("INSERT INTO match_user_state VALUES (1, TRUE)");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MatchDatabaseCleanup.clear(transactionManager, database);
            status.setRollbackOnly();
        });

        assertThat(count("match_participant")).isZero();
        assertThat(count("\"match\"")).isZero();
        assertThat(count("processed_event")).isZero();
        assertThat(count("match_user_state")).isZero();
    }

    @Test
    void skipsMissingTablesAndLeavesOtherSchemasUntouched() {
        createMatchTables();
        jdbc.execute("CREATE SCHEMA other_schema");
        jdbc.execute("CREATE TABLE other_schema.\"match\" (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO other_schema.\"match\" VALUES (2)");

        MatchDatabaseCleanup.clear(transactionManager, database);

        assertThat(count("match_participant")).isZero();
        assertThat(count("\"match\"")).isZero();
        assertThat(count("other_schema.\"match\"")).isEqualTo(1);
    }

    @Test
    void allowsSchemaWithoutMatchTables() {
        jdbc.execute("CREATE TABLE unrelated (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO unrelated VALUES (1)");

        MatchDatabaseCleanup.clear(transactionManager, database);

        assertThat(count("unrelated")).isEqualTo(1);
    }

    private void createMatchTables() {
        // 예약어는 소문자로 인용하고 참가 테이블은 인용하지 않아 H2 이름 처리도 검증한다.
        jdbc.execute("CREATE TABLE \"match\" (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE match_participant (id BIGINT PRIMARY KEY, "
                + "match_id BIGINT REFERENCES \"match\"(id))");
        jdbc.update("INSERT INTO \"match\" VALUES (1)");
        jdbc.update("INSERT INTO match_participant VALUES (1, 1)");
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
