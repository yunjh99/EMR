import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Properties;

/** Read-only export of current MySQL DDL. Does not export patient/account rows or credentials. */
public class ExportDeploymentSchema {
    public static void main(String[] args) throws Exception {
        Properties settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            settings.load(reader);
        }
        StringBuilder sql = new StringBuilder("-- MySQL schema snapshot; new empty demo database only.\n"
                + "-- No account/patient data. Existing constraints are preserved.\n"
                + "SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
        int count = 0;
        try (var connection = DriverManager.getConnection(setting(settings, "url"),
                setting(settings, "username"), setting(settings, "password"))) {
            var tables = new ArrayList<String>();
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            tables.sort(String::compareTo);
            for (String table : tables) {
                if (!table.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Unexpected table name");
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                    rows.next();
                    sql.append(rows.getString(2).replaceAll(" AUTO_INCREMENT=\\d+", "")).append(";\n\n");
                    count++;
                }
            }
        }
        sql.append("SET FOREIGN_KEY_CHECKS=1;\n");
        Path output = Path.of("deployment/mysql/001-schema.sql");
        Files.createDirectories(output.getParent());
        Files.writeString(output, sql);
        System.out.println("Exported schema only: tables=" + count + ", output=" + output);
    }

    private static String setting(Properties settings, String key) {
        String env = System.getenv("SPRING_DATASOURCE_" + key.toUpperCase());
        String value = env != null ? env : settings.getProperty("spring.datasource." + key);
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String[] expression = value.substring(2, value.length() - 1).split(":", 2);
            value = System.getenv(expression[0]);
            if (value == null && expression.length == 2) value = expression[1];
        }
        if (value == null) throw new IllegalStateException("Missing datasource " + key);
        return value;
    }
}
