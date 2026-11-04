package com.example.sporty.support;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 작업 스레드와 리스너가 종료된 뒤 커밋된 테스트 데이터를 별도 트랜잭션에서 정리한다. */
public final class MatchDatabaseCleanup {

    private static final String[] TABLES = {
            "match_participant", "match", "match_point_debit", "match_point_refund", "processed_event", "match_user_state"
    };

    private MatchDatabaseCleanup() {
    }

    public static void clear(PlatformTransactionManager transactionManager, DataSource dataSource) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        transactions.executeWithoutResult(status -> jdbc.execute((ConnectionCallback<Void>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, String> existingTables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            // 현재 연결의 catalog/schema만 조회하고, 인용된 H2 테이블명의 대소문자도 보존한다.
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    existingTables.put(name, name);
                }
            }
            String quote = metadata.getIdentifierQuoteString().trim();
            for (String table : TABLES) {
                String name = existingTables.get(table);
                if (name != null) {
                    // JDBC가 제공하는 인용자로 예약어 match도 H2·MariaDB에서 안전하게 삭제한다.
                    jdbc.update("DELETE FROM " + quote + name + quote);
                }
            }
            return null;
        }));
    }
}
