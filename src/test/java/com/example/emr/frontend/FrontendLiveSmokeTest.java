package com.example.emr.frontend;

import com.example.emr.EmrApplication;
import com.example.emr.appointments.domain.*;
import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.encounters.dto.EncounterCreateRequest;
import com.example.emr.encounters.service.EncounterService;
import com.example.emr.global.security.JwtProvider;
import com.example.emr.patients.domain.*;
import com.example.emr.users.domain.*;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.visits.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Full real backend + isolated MySQL + browser. No mock login endpoint or fabricated JWT. */
@EnabledIfEnvironmentVariable(named = "FRONTEND_TEST_MYSQL_URL", matches = "jdbc:mysql://.+/emr_bench_frontend_.+")
@SpringBootTest(classes = EmrApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=18080", "server.address=127.0.0.1", "spring.jpa.hibernate.ddl-auto=validate",
                "cors.allowed-origins=http://127.0.0.1:5177",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "spring.sql.init.mode=never",
                "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FrontendLiveSmokeTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EncounterService encounters;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    static final String TEST_SECRET = UUID.randomUUID().toString() + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("FRONTEND_TEST_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/emr_bench_frontend_[a-zA-Z0-9_]+(?:\\?.*)?"))
            throw new IllegalStateException("A fresh isolated emr_bench_frontend_ schema is required");
        Properties settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) { settings.load(reader); }
        String username = setting(settings, "username"), password = setting(settings, "password");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("jwt.secret-key", () -> TEST_SECRET);
        try (var connection = DriverManager.getConnection(url, username, password)) {
            if (!connection.getCatalog().startsWith("emr_bench_frontend_")) throw new IllegalStateException("Unexpected schema");
            for (String file : List.of("db/visits_encounters.sql", "db/encounter_histories.sql")) {
                for (String sql : Files.readString(Path.of(file)).replaceAll("(?m)^--.*$", "").split(";"))
                    if (!sql.isBlank()) {
                        String trimmed = sql.trim();
                        var table = java.util.regex.Pattern.compile("(?is)^CREATE TABLE (\\w+)").matcher(trimmed);
                        if (table.find()) {
                            try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
                                query.setString(1, table.group(1));
                                try (var rows = query.executeQuery()) { rows.next(); if (rows.getLong(1) > 0) continue; }
                            }
                        }
                        if (trimmed.startsWith("ALTER TABLE encounters ADD COLUMN version")) {
                            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='encounters' AND column_name='version'")) {
                                rows.next(); if (rows.getLong(1) > 0) continue;
                            }
                        }
                        try (var statement = connection.createStatement()) { statement.execute(sql); }
                    }
            }
        }
    }

    static String setting(Properties properties, String key) {
        String value = System.getenv("FRONTEND_TEST_MYSQL_" + key.toUpperCase());
        if (value == null) value = properties.getProperty("spring.datasource." + key);
        if (value.startsWith("${") && value.endsWith("}")) {
            String[] parts = value.substring(2, value.length() - 1).split(":", 2);
            value = System.getenv(parts[0]);
            if (value == null && parts.length == 2) value = parts[1];
        }
        return Objects.requireNonNull(value);
    }

    @Test
    void connectedScreensWorkAgainstRealControllers() throws Exception {
        Map<Role, User> actors = new EnumMap<>(Role.class);
        long[] ids = new TransactionTemplate(transactionManager).execute(tx -> {
            for (Role role : Role.values()) {
                var user = new User(role == Role.NURSE ? "테스트간호사" : "qa_" + role.name().toLowerCase(), passwords.encode("Browser-test-only-73!"), "테스트 " + role.name(), role);
                em.persist(user); actors.put(role, user);
            }
            var department = new Department("QA_IM", "테스트 내과"); em.persist(department);
            var doctor = new Doctor(actors.get(Role.DOCTOR), department, "QA-LICENSE", "테스트 진료"); em.persist(doctor);
            var patient = new Patient("QA00001", "가상환자", "010-0000-0000", "테스트 주소", Gender.F, LocalDate.of(1990, 1, 1)); em.persist(patient);
            for (int i = 0; i < 18; i++) {
                var slot = BeanUtils.instantiateClass(AppointmentSlot.class);
                ReflectionTestUtils.setField(slot, "doctorId", doctor.getId());
                ReflectionTestUtils.setField(slot, "slotAt", LocalDate.now().atTime(9, 0).plusMinutes(i * 30L));
                em.persist(slot);
            }
            em.persist(new Appointment(doctor, patient, LocalDate.now().atTime(9, 0), "가상 예약"));
            var visit = new Visit(patient, null, department, doctor, VisitType.WALK_IN); visit.start(); em.persist(visit);
            em.flush(); return new long[]{department.getId(), doctor.getId(), patient.getId(), visit.getId()};
        });
        User doctorUser = actors.get(Role.DOCTOR);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(doctorUser.getId(), Role.DOCTOR), null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        SecurityContextHolder.setContext(context);
        Long encounter = encounters.createEncounter(new EncounterCreateRequest(ids[3], "가상 증상", "정정 전 테스트 소견", "테스트 치료 계획"), new LoginUser(doctorUser.getId(), Role.DOCTOR));
        SecurityContextHolder.clearContext();
        Map<String, Object> fixture = new LinkedHashMap<>();
        Map<String, Object> accounts = new LinkedHashMap<>();
        for (var entry : actors.entrySet()) accounts.put(entry.getKey().name(), Map.of("id", entry.getValue().getId(),
                "token", jwt.createToken(entry.getValue().getUsername()).getAccessToken().getData(),
                "username", entry.getValue().getUsername(), "password", "Browser-test-only-73!"));
        fixture.put("accounts", accounts); fixture.put("departmentId", ids[0]); fixture.put("doctorId", ids[1]);
        fixture.put("patientId", ids[2]); fixture.put("encounterId", encounter);
        Path tokenFile = Path.of("build/frontend-smoke/session.json").toAbsolutePath();
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, mapper.writeValueAsString(fixture));
        Process browser = null;
        try {
            var builder = new ProcessBuilder("node", "tests/live-smoke.mjs").directory(Path.of("frontend").toFile());
            builder.environment().put("EMR_SMOKE_FIXTURE", tokenFile.toString());
            builder.redirectErrorStream(true).redirectOutput(Path.of("build/frontend-smoke/browser.log").toFile());
            browser = builder.start();
            assertThat(browser.waitFor(180, TimeUnit.SECONDS)).as("Browser smoke test timeout").isTrue();
            assertThat(browser.exitValue()).withFailMessage("Browser test failed. See build/frontend-smoke/browser.log (no credentials logged)").isZero();
        } finally {
            if (browser != null && browser.isAlive()) browser.destroyForcibly();
            Files.deleteIfExists(tokenFile);
        }
    }

    @AfterEach
    void cleanSyntheticData() {
        SecurityContextHolder.clearContext();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).startsWith("emr_bench_frontend_");
        for (String table : List.of("encounter_histories", "encounters", "visits", "appointment_slots", "appointments",
                "patient_histories", "audit_logs", "doctors", "patients", "patient_number_sequences", "departments", "users"))
            jdbc.update("DELETE FROM " + table);
    }
}
