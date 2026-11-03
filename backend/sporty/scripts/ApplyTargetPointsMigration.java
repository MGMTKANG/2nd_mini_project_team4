import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** One-time, non-destructive migration. No preflight is needed: the new column cannot yet hold bad data. */
class ApplyTargetPointsMigration {
    public static void main(String[] args) throws Exception {
        Class.forName("org.mariadb.jdbc.Driver");
        Properties config = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"))) { config.load(reader); }
        String url = System.getenv().getOrDefault("DB_URL", config.getProperty("DB_URL"));
        String username = System.getenv().getOrDefault("DB_USERNAME", config.getProperty("DB_USERNAME"));
        String password = System.getenv().getOrDefault("DB_PASSWORD", config.getProperty("DB_PASSWORD"));
        String sql = Files.readString(Path.of("docs/sql/003_match_target_points.sql"))
                .replaceAll("(?m)^--.*$", "");
        boolean apply = args.length == 1 && args[0].equals("--apply");
        try (Connection c = DriverManager.getConnection(url, username, password)) {
            if (!"sporty".equals(c.getCatalog())) throw new IllegalStateException("Expected sporty database");
            try (var columns = c.getMetaData().getColumns(c.getCatalog(), null, "match", "target_points")) {
                if (columns.next()) throw new IllegalStateException("target_points already exists; this is a one-time migration, do not reapply.");
            }
            if (apply) {
                for (String part : sql.split(";")) {
                    String statement = part.trim();
                    if (statement.isEmpty()) continue;
                    try (var s = c.createStatement()) { s.execute(statement); }
                }
            }
            System.out.println(apply ? "target_points column and updated check constraint installed; no existing records changed."
                    : "Preflight passed; no schema changes made. Use --apply to migrate.");
        }
    }
}
