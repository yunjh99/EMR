import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Java 21 source-file utility; put mysql-connector-j on --class-path. Never prints credentials. */
public class EncounterHistorySchemaTool {
    static String resolve(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String[] parts = value.substring(2, value.length() - 1).split(":", 2);
            String env = System.getenv(parts[0]);
            if (env != null) return env;
            if (parts.length == 2) return parts[1];
            throw new IllegalStateException("Missing environment variable: " + parts[0]);
        }
        return value;
    }

    static long count(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getLong(1);
        }
    }

    static void inspect(Connection c) throws SQLException {
        System.out.println("schema=" + c.getCatalog() + " database=" + c.getMetaData().getDatabaseProductVersion());
        for (String table : List.of("encounters", "encounter_histories", "visits")) {
            if (count(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='" + table + "'") == 0) {
                System.out.println("missingTable=" + table); continue;
            }
            try (var statement = c.createStatement(); var result = statement.executeQuery("SHOW CREATE TABLE " + table)) {
                result.next(); System.out.println(result.getString(2));
            }
            System.out.println(table + "Rows=" + count(c, "SELECT COUNT(*) FROM " + table));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("inspect [schema] | migrate schema");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) { properties.load(reader); }
        for (String key : List.of("url", "username", "password")) {
            String env = System.getenv("ENCOUNTER_TEST_MYSQL_" + key.toUpperCase());
            properties.setProperty("spring.datasource." + key, env != null ? env : resolve(properties.getProperty("spring.datasource." + key)));
        }
        try (var c = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"))) {
            if (!c.getMetaData().getDatabaseProductName().equals("MySQL")) throw new IllegalStateException("Requires MySQL");
            if (args.length > 1) {
                if (!args[1].matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid schema");
                c.setCatalog(args[1]);
            }
            if (args[0].equals("inspect")) { inspect(c); return; }
            if (!args[0].equals("migrate") || args.length != 2) throw new IllegalArgumentException("migrate requires an explicit schema");
            if (count(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('encounters','users')") != 2)
                throw new IllegalStateException("Existing encounters/users tables are required; no changes made");
            if (count(c, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='encounters' AND column_name='version'") != 0
                    || count(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='encounter_histories'") != 0)
                throw new IllegalStateException("Migration objects already exist; inspect DDL before resuming; no changes made");
            for (String sql : Files.readString(Path.of("db/encounter_histories.sql")).replaceAll("(?m)^--.*$", "").split(";")) {
                if (!sql.isBlank()) try (var statement = c.createStatement()) { statement.execute(sql); }
            }
            System.out.println("migrationApplied=" + c.getCatalog()); inspect(c);
        }
    }
}
