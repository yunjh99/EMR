import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Java 21 source-file tool; run with mysql-connector-j on --class-path. No credentials are printed. */
public class AppointmentSchemaTool {
    static String resolve(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String[] p = value.substring(2, value.length() - 1).split(":", 2);
            String env = System.getenv(p[0]);
            if (env != null) return env;
            if (p.length == 2) return p[1];
            throw new IllegalStateException("Missing environment variable: " + p[0]);
        }
        return value;
    }
    static String name(String name) {
        if (!name.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid schema name");
        return "`" + name + "`";
    }
    static long scalar(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
    static void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }
    static void inspect(Connection c) throws SQLException {
        System.out.println("schema=" + c.getCatalog() + " MySQL=" + c.getMetaData().getDatabaseProductVersion());
        try (var s = c.createStatement(); var r = s.executeQuery("""
                SELECT doctor_id, scheduled_at, COUNT(*) FROM appointments WHERE status='SCHEDULED'
                GROUP BY doctor_id, scheduled_at HAVING COUNT(*) > 1
                """)) {
            int count = 0;
            while (r.next()) { count++; System.out.println("DUPLICATE doctor=" + r.getLong(1) + " slot=" + r.getString(2) + " count=" + r.getLong(3)); }
            System.out.println("duplicateActiveGroups=" + count);
        }
        try (var s = c.createStatement(); var r = s.executeQuery("SHOW CREATE TABLE appointments")) {
            r.next(); System.out.println(r.getString(2));
        }
    }
    static void apply(Connection c) throws Exception {
        inspect(c);
        if (scalar(c, "SELECT COUNT(*) FROM (SELECT 1 FROM appointments WHERE status='SCHEDULED' GROUP BY doctor_id, scheduled_at HAVING COUNT(*)>1) d") != 0)
            throw new IllegalStateException("Active duplicates exist; no data or schema changed");
        long columns = scalar(c, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='appointments' AND column_name='active_slot_at'");
        long indexes = scalar(c, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='appointments' AND index_name='uk_active_doctor_slot'");
        if (columns != 0 || indexes != 0) throw new IllegalStateException("Object already exists: inspect definition; no changes made");
        String script = Files.readString(Path.of("db/appointments_active_slot.sql"));
        execute(c, script.substring(script.indexOf("ALTER TABLE")));
        inspect(c);
    }
    static void cloneSchema(Connection c, String target) throws Exception {
        if (!target.startsWith("emr_bench_")) throw new IllegalArgumentException("Only new emr_bench_ schemas allowed");
        String source = c.getCatalog();
        Set<String> remaining = new TreeSet<>();
        Map<String, Set<String>> parents = new HashMap<>();
        Map<String, String> definitions = new HashMap<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")) {
            while (r.next()) remaining.add(r.getString(1));
        }
        for (String table : remaining) {
            try (var s = c.createStatement(); var r = s.executeQuery("SHOW CREATE TABLE " + name(table))) {
                r.next(); definitions.put(table, r.getString(2).replace(name(source) + ".", name(target) + "."));
            }
            parents.put(table, new HashSet<>());
            try (var p = c.prepareStatement("SELECT referenced_table_name FROM information_schema.key_column_usage WHERE table_schema=? AND table_name=? AND referenced_table_name IS NOT NULL")) {
                p.setString(1, source); p.setString(2, table);
                try (var r = p.executeQuery()) { while (r.next()) if (!table.equals(r.getString(1))) parents.get(table).add(r.getString(1)); }
            }
        }
        // CREATE DATABASE intentionally fails if it exists. Never overwrite/drop an existing schema.
        execute(c, "CREATE DATABASE " + name(target) + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        c.setCatalog(target);
        Set<String> created = new HashSet<>();
        while (!remaining.isEmpty()) {
            String table = remaining.stream().filter(t -> created.containsAll(parents.get(t))).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cyclic/external FK; inspect partially created new schema"));
            execute(c, definitions.get(table));
            created.add(table); remaining.remove(table);
        }
        System.out.println("clonedEmptySchema=" + target + " tables=" + created.size() + " (original FK/UNIQUE preserved)");
    }
    static void exportBaseline(Connection c) throws Exception {
        if (scalar(c, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='appointments' AND column_name='active_slot_at'") != 0)
            throw new IllegalStateException("Baseline export requires the original pre-migration schema");
        StringBuilder sql = new StringBuilder("-- Original MySQL baseline for isolated benchmark databases only. No data.\n");
        for (String table : List.of("users", "departments", "patients", "doctors", "audit_logs",
                "patient_histories", "patient_number_sequences", "appointments")) {
            try (var s = c.createStatement(); var r = s.executeQuery("SHOW CREATE TABLE " + name(table))) {
                r.next(); sql.append(r.getString(2).replaceAll(" AUTO_INCREMENT=\\d+", "")).append(";\n\n");
            }
        }
        Path file = Path.of("src/test/resources/mysql/appointment-baseline-schema.sql");
        Files.createDirectories(file.getParent()); Files.writeString(file, sql);
        System.out.println("baselineExport=" + file);
    }
    static void createBaseline(Connection c, String target) throws Exception {
        if (!target.startsWith("emr_bench_")) throw new IllegalArgumentException("Only new emr_bench_ schemas allowed");
        execute(c, "CREATE DATABASE " + name(target) + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        c.setCatalog(target);
        for (String statement : Files.readString(Path.of("src/test/resources/mysql/appointment-baseline-schema.sql")).split(";"))
            if (!statement.isBlank()) execute(c, statement);
        System.out.println("createdOriginalBaseline=" + target);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("inspect | apply [schema] | clone emr_bench_<name>");
        var p = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) { p.load(reader); }
        for (String key : List.of("url", "username", "password")) {
            String env = System.getenv("APPOINTMENT_TEST_MYSQL_" + key.toUpperCase());
            p.setProperty("spring.datasource." + key, env != null ? env : resolve(p.getProperty("spring.datasource." + key)));
        }
        try (var c = DriverManager.getConnection(p.getProperty("spring.datasource.url"), p.getProperty("spring.datasource.username"), p.getProperty("spring.datasource.password"))) {
            if (!"MySQL".equals(c.getMetaData().getDatabaseProductName())) throw new IllegalStateException("Requires MySQL");
            if (args[0].equals("clone")) cloneSchema(c, args[1]);
            else if (args[0].equals("create-baseline")) createBaseline(c, args[1]);
            else {
                if (args.length > 1) c.setCatalog(args[1]);
                if (args[0].equals("apply")) apply(c);
                else if (args[0].equals("export-baseline")) exportBaseline(c);
                else if (args[0].equals("inspect")) inspect(c);
                else throw new IllegalArgumentException("Unknown command");
            }
        }
    }
}
