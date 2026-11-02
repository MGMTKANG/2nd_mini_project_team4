package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.sporty.support.MatchDatabaseCleanup;

import jakarta.persistence.EntityManagerFactory;

/** Hibernate가 테이블을 만들기 전에 실제 운영 SQL만으로 빈 DB를 준비한다. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class MatchSchemaMariaDBIntegrationTest {
    private static final FileSystemResource DEPLOYMENT_SQL =
            new FileSystemResource("docs/sql/2026-11-02-match-schema.sql");
    private static final List<String> TABLES =
            List.of("match", "match_participant", "processed_event", "match_user_state");
    private static final LocalDateTime START_AT = LocalDateTime.of(2030, 1, 1, 10, 0);

    @Autowired private ConfigurableApplicationContext context;
    @Autowired private Environment environment;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        MariaDBContainer<?> database = SchemaDatabase.INSTANCE;
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.datasource.driver-class-name", database::getDriverClassName);
    }

    private static final class SchemaDatabase {
        // 기존 create-drop 컨테이너와 분리한다. Docker 조건을 통과한 뒤에만 초기화한다.
        // 클래스별 stop 없이 JVM 종료 시 Testcontainers 리소스 정리기가 제거한다.
        private static final MariaDBContainer<?> INSTANCE = startWithDeploymentSql();

        private static MariaDBContainer<?> startWithDeploymentSql() {
            MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11.4.8")
                    .withDatabaseName("match_schema_test")
                    .withUsername("match_schema_test")
                    .withPassword("match_schema_test");
            database.start();
            try (Connection connection = DriverManager.getConnection(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword())) {
                try (var statement = connection.createStatement();
                        var tables = statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                    assertThat(tables.next()).isTrue();
                    assertThat(tables.getInt(1)).as("운영 SQL 적용 전 빈 스키마").isZero();
                }
                ScriptUtils.executeSqlScript(connection, DEPLOYMENT_SQL);
            } catch (SQLException exception) {
                throw new IllegalStateException("빈 MariaDB에 운영 스키마를 적용하지 못했습니다.", exception);
            }
            return database;
        }
    }

    @AfterEach
    void cleanUp() {
        MatchDatabaseCleanup.clear(transactionManager, dataSource);
    }

    @Test
    void deploymentSqlBootsWithValidate() {
        assertThat(context.isActive()).isTrue();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        for (String property : List.of("spring.cloud.config.enabled", "eureka.client.enabled",
                "spring.kafka.listener.auto-startup", "spring.kafka.admin.auto-create")) {
            assertThat(environment.getProperty(property, Boolean.class)).as(property).isFalse();
        }
        assertThat(jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
                String.class)).containsExactlyInAnyOrderElementsOf(TABLES);
    }

    @Test
    void deploymentSqlMatchesEntityConstraints() {
        assertColumns("match",
                new Column("id", "bigint", null, false),
                new Column("title", "varchar", 50L, false),
                new Column("description", "varchar", 500L, true),
                new Column("start_at", "datetime", null, false),
                new Column("end_at", "datetime", null, false),
                new Column("max_participant", "int", null, false),
                new Column("status", "varchar", 10L, false),
                new Column("created_at", "timestamp", null, false),
                new Column("updated_at", "timestamp", null, false),
                new Column("skill_level", "varchar", 30L, false),
                new Column("sport_type", "varchar", 15L, false),
                new Column("gender_group", "varchar", 10L, false),
                new Column("service_id", "bigint", null, false));
        assertColumns("match_participant",
                new Column("match_participant_id", "bigint", null, false),
                new Column("user_id", "bigint", null, false),
                new Column("match_id", "bigint", null, false),
                new Column("created_at", "timestamp", null, false),
                new Column("updated_at", "timestamp", null, false),
                new Column("role", "varchar", 20L, false));
        assertColumns("processed_event",
                new Column("consumer_name", "varchar", 100L, false),
                new Column("event_id", "char", 36L, false),
                new Column("processed_at", "datetime", null, false));
        assertColumns("match_user_state",
                new Column("user_id", "bigint", null, false),
                new Column("withdrawn", "tinyint", null, false));
        assertThat(jdbc.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='match_user_state' AND COLUMN_NAME='withdrawn'
                """, String.class)).isEqualTo("tinyint(1)");
        assertThat(jdbc.queryForList(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
                String.class)).hasSize(4).containsOnly("InnoDB");
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND EXTRA LIKE '%auto_increment%'
                """, String.class)).containsExactlyInAnyOrder("match.id", "match_participant.match_participant_id");
        assertThat(constraintColumns("match", "PRIMARY")).containsExactly("id");
        assertThat(constraintColumns("match_participant", "PRIMARY")).containsExactly("match_participant_id");
        assertThat(constraintColumns("match_user_state", "PRIMARY")).containsExactly("user_id");
        assertThat(constraintColumns("processed_event", "PRIMARY")).containsExactly("consumer_name", "event_id");
        assertThat(constraintColumns("match_participant", "uk_match_participant_match_user"))
                .containsExactly("match_id", "user_id");
        assertThat(jdbc.queryForObject("""
                SELECT CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='match_participant'
                  AND CONSTRAINT_NAME='uk_match_participant_match_user'
                """, String.class)).isEqualTo("UNIQUE");
        // 전체 FK 목록을 대조해 서비스 간 FK가 생기지 않았는지도 확인한다.
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, '->', REFERENCED_TABLE_NAME, '.', REFERENCED_COLUMN_NAME)
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
                """, String.class)).containsExactly("match_participant.match_id->match.id");
        String check = jdbc.queryForObject("""
                SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='match' AND CONSTRAINT_NAME='ck_match_valid_fields'
                """, String.class);
        assertThat(Objects.requireNonNull(check).toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", ""))
                .contains("char_length(trim(title))>0", "max_participant>=1", "end_at>start_at", "service_id>0");
        assertThat(columnDefault("match", "status")).isEqualTo("'RECRUITING'");
        assertThat(columnDefault("match", "skill_level")).isEqualTo("'BEGINNER'");
        assertThat(columnDefault("match_user_state", "withdrawn")).isEqualTo("0");
    }

    @Test
    void deploymentSqlEnforcesConstraints() {
        // DB 최소 정원은 1이다. API 최소 정원 2와 구분한다.
        long matchId = insertMatch("운영 SQL 기본값", 1, START_AT, START_AT.plusHours(1), 100L);
        assertThat(matchId).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM `match` WHERE id=?", String.class, matchId))
                .isEqualTo("RECRUITING");
        assertThat(jdbc.queryForObject("SELECT skill_level FROM `match` WHERE id=?", String.class, matchId))
                .isEqualTo("BEGINNER");
        assertThat(jdbc.queryForObject("SELECT description FROM `match` WHERE id=?", String.class, matchId)).isNull();
        insertParticipant(matchId, 101L);
        assertThatThrownBy(() -> insertParticipant(matchId, 101L)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertParticipant(matchId + 1, 102L)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM `match` WHERE id=?", matchId))
                .isInstanceOf(DataIntegrityViolationException.class);

        String eventId = UUID.randomUUID().toString();
        insertEvent("consumer-a", eventId);
        assertThatThrownBy(() -> insertEvent("consumer-a", eventId)).isInstanceOf(DataIntegrityViolationException.class);
        insertEvent("consumer-b", eventId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id=?", Long.class, eventId))
                .isEqualTo(2L);
        jdbc.update("INSERT INTO match_user_state (user_id) VALUES (?)", 101L);
        assertThat(jdbc.queryForObject("SELECT withdrawn FROM match_user_state WHERE user_id=?", Boolean.class, 101L))
                .isFalse();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO match_user_state (user_id) VALUES (?)", 101L))
                .isInstanceOf(DataIntegrityViolationException.class);

        // TIMESTAMP의 NULL 대입 처리는 서버 옵션 영향을 받으므로 NOT NULL 메타데이터로 확인한다.
        assertNullsRejected("match", List.of("title", "start_at", "end_at", "max_participant", "status",
                "skill_level", "sport_type", "gender_group", "service_id"));
        assertNullsRejected("match_participant", List.of("user_id", "match_id", "role"));
        assertNullsRejected("processed_event", List.of("consumer_name", "event_id", "processed_at"));
        assertNullsRejected("match_user_state", List.of("user_id", "withdrawn"));
        for (String title : List.of("", "   ")) {
            assertThatThrownBy(() -> insertMatch(title, 1, START_AT, START_AT.plusHours(1), 100L))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertMatch("정원 오류", 0, START_AT, START_AT.plusHours(1), 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (LocalDateTime endAt : List.of(START_AT, START_AT.minusHours(1))) {
            assertThatThrownBy(() -> insertMatch("시간 오류", 1, START_AT, endAt, 100L))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (long serviceId : List.of(0L, -1L)) {
            assertThatThrownBy(() -> insertMatch("시설 오류", 1, START_AT, START_AT.plusHours(1), serviceId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void reapplyingDeploymentSqlPreservesData() throws Exception {
        long matchId = insertMatch("재적용 보존", 2, START_AT, START_AT.plusHours(1), 100L);
        jdbc.update("UPDATE `match` SET description=?, status=? WHERE id=?", "수정된 설명", "CLOSED", matchId);
        insertParticipant(matchId, 101L);
        insertEvent("consumer-a", UUID.randomUUID().toString());
        jdbc.update("INSERT INTO match_user_state (user_id, withdrawn) VALUES (?, TRUE)", 101L);
        Map<String, List<Map<String, Object>>> before = tableData();
        before.forEach((table, rows) -> assertThat(rows).as(table).hasSize(1));

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, DEPLOYMENT_SQL);
        }
        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
        assertThat(tableData()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT withdrawn FROM match_user_state WHERE user_id=?", Boolean.class, 101L))
                .isTrue();
    }

    private void assertColumns(String table, Column... expected) {
        List<Column> actual = jdbc.query("""
                SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
                FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                ORDER BY ORDINAL_POSITION
                """, (rs, row) -> {
                    Number length = (Number) rs.getObject("CHARACTER_MAXIMUM_LENGTH");
                    return new Column(rs.getString("COLUMN_NAME"), rs.getString("DATA_TYPE"),
                            length == null ? null : length.longValue(), "YES".equals(rs.getString("IS_NULLABLE")));
                }, table);
        assertThat(actual).as(table).containsExactly(expected);
    }

    private List<String> constraintColumns(String table, String constraint) {
        return jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?
                ORDER BY ORDINAL_POSITION
                """, String.class, table, constraint);
    }

    private String columnDefault(String table, String column) {
        return jdbc.queryForObject("""
                SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                """, String.class, table, column);
    }

    private void assertNullsRejected(String table, List<String> columns) {
        for (String column : columns) {
            // 이름은 이 테스트 내 고정 목록에서만 받는다. 각 실패 문장은 자동 롤백된다.
            assertThatThrownBy(() -> jdbc.update("UPDATE `" + table + "` SET `" + column + "`=NULL"))
                    .as("%s.%s NOT NULL", table, column).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private long insertMatch(String title, int capacity, LocalDateTime startAt, LocalDateTime endAt, long serviceId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        Object[] values = {title, startAt, endAt, capacity, "SOCCER", "MIXED", serviceId,
                Timestamp.valueOf(START_AT), Timestamp.valueOf(START_AT)};
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO `match` (title, start_at, end_at, max_participant, sport_type, gender_group,
                                         service_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void insertParticipant(long matchId, long userId) {
        jdbc.update("""
                INSERT INTO match_participant (user_id, match_id, created_at, updated_at, role)
                VALUES (?, ?, ?, ?, 'OWNER')
                """, userId, matchId, Timestamp.valueOf(START_AT), Timestamp.valueOf(START_AT));
    }

    private void insertEvent(String consumer, String eventId) {
        jdbc.update("INSERT INTO processed_event (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                consumer, eventId, START_AT);
    }

    private Map<String, List<Map<String, Object>>> tableData() {
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (String table : TABLES) {
            data.put(table, jdbc.queryForList("SELECT * FROM `" + table + "`"));
        }
        return data;
    }

    private record Column(String name, String type, Long length, boolean nullable) {
    }
}
