package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.AppointmentStatus;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.departments.domain.Department;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.doctors.repository.DoctorRepository;
import com.example.emr.global.error.exception.DuplicateAppointmentException;
import com.example.emr.patients.domain.*;
import com.example.emr.patients.repository.PatientRepository;
import com.example.emr.users.domain.*;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL, committed fixtures, no outer test transaction. */
@SpringBootTest
@TestPropertySource(locations = {"file:src/main/resources/application.properties",
        "classpath:application-appointment-concurrency.properties"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AppointmentMysqlTestSupport {
    @Autowired AppointmentService service;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    User staff;
    Patient patient;
    Department department;
    final List<User> doctorUsers = new ArrayList<>();
    final List<Doctor> fixtureDoctors = new ArrayList<>();
    boolean cleanupSafe = true;

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry r) {
        Map.of("APPOINTMENT_TEST_MYSQL_URL", "spring.datasource.url",
                "APPOINTMENT_TEST_MYSQL_USERNAME", "spring.datasource.username",
                "APPOINTMENT_TEST_MYSQL_PASSWORD", "spring.datasource.password",
                "APPOINTMENT_TEST_ISOLATION", "spring.datasource.hikari.transaction-isolation")
                .forEach((env, key) -> { String v = System.getenv(env); if (v != null) r.add(key, () -> v); });
    }

    void fixtures(int doctorCount) throws Exception {
        try (var c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        staff = users.saveAndFlush(new User("uq_s_" + suffix, "test-hash", "예약 실험 직원", Role.STAFF));
        department = departments.saveAndFlush(new Department("UQ_" + suffix, "예약 실험"));
        patient = patients.saveAndFlush(new Patient("UQ_" + suffix, "실험 환자", "010-0000-0000",
                "테스트 주소", Gender.F, LocalDate.of(1990, 1, 1)));
        for (int i = 0; i < doctorCount; i++) {
            var u = users.saveAndFlush(new User("uq_" + suffix + "_" + i, "test-hash", "실험 의사", Role.DOCTOR));
            doctorUsers.add(u);
            fixtureDoctors.add(doctors.saveAndFlush(new Doctor(u, department, "UQ_" + suffix + "_" + i, "내과")));
        }
    }

    List<AppointmentCreateRequest> requests(String scenario) {
        LocalDate day = LocalDate.now().plusDays(10);
        List<AppointmentCreateRequest> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Doctor doctor = fixtureDoctors.get(scenario.equals("C") ? i : 0);
            LocalDateTime slot = scenario.equals("B")
                    ? day.plusDays(i / 18).atTime(9, 0).plusMinutes((i % 18) * 30L)
                    : day.atTime(9, 0);
            requests.add(new AppointmentCreateRequest(doctor, patient, slot, "예약 실험"));
        }
        return requests;
    }

    void authenticate() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(staff.getId(), Role.STAFF),
                null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        SecurityContextHolder.setContext(context);
    }

    Sample execute(List<AppointmentCreateRequest> requests, Function<AppointmentCreateRequest, Long> create,
                   boolean monitoring) throws Exception {
        var executor = Executors.newFixedThreadPool(100);
        var ready = new CountDownLatch(requests.size());
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(requests.size());
        List<Future<Attempt>> futures = new ArrayList<>();
        Monitor monitor = null;
        try {
            for (var request : requests) futures.add(executor.submit(() -> {
                try {
                    authenticate(); ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) throw new TimeoutException("Start timeout");
                    long began = System.nanoTime();
                    try {
                        Long id = create.apply(request);
                        return new Attempt(id, "SUCCESS", "", System.nanoTime() - began);
                    } catch (DuplicateAppointmentException e) {
                        return new Attempt(null, "DUPLICATE", e.getClass().getName(), System.nanoTime() - began);
                    } catch (Throwable e) {
                        Throwable root = e;
                        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                        String type = e.getClass().getName() + "|root=" + root.getClass().getName();
                        if (root instanceof SQLException sql) type += "|sqlState=" + sql.getSQLState() + "|sqlCode=" + sql.getErrorCode();
                        return new Attempt(null, "ERROR", type, System.nanoTime() - began);
                    }
                } finally { SecurityContextHolder.clearContext(); done.countDown(); }
            }));
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("100 workers ready").isTrue();
            Map<String, Long> before = monitoring ? globalLockCounters() : Map.of();
            if (monitoring) monitor = new Monitor(dataSource.unwrap(HikariDataSource.class));
            long began = System.nanoTime();
            start.countDown();
            if (!done.await(120, TimeUnit.SECONDS)) throw new TimeoutException("Requests incomplete: " + done.getCount());
            long elapsed = System.nanoTime() - began;
            if (monitor != null) monitor.stop();
            List<Attempt> attempts = new ArrayList<>();
            for (var f : futures) attempts.add(f.get());
            Map<String, Long> after = monitoring ? globalLockCounters() : Map.of();
            long lockMs = delta(before, after, "Innodb_row_lock_time");
            long lockWaits = delta(before, after, "Innodb_row_lock_waits");
            Sample sample = new Sample(attempts, elapsed / 1_000_000.0, storedCount(), activeCount(),
                    lockMs, lockWaits, monitor == null ? 0 : monitor.maxPending,
                    monitor == null ? 0 : monitor.maxActive, monitor == null ? -1 : monitor.maxDbWaiters,
                    monitor == null ? "OFF" : monitor.dbMetricStatus);
            // AOP swallows audit write errors: check every outcome independently after timing.
            assertAudit(sample);
            return sample;
        } finally {
            start.countDown(); executor.shutdownNow();
            cleanupSafe = executor.awaitTermination(30, TimeUnit.SECONDS);
            if (monitor != null) monitor.close();
            assertThat(cleanupSafe).as("Workers must stop before fixture cleanup").isTrue();
        }
    }

    void assertAudit(Sample sample) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=?", Long.class, staff.getId()))
                .as("Each completed request has one audit entry").isEqualTo(sample.attempts.size());
        List<Long> auditIds = jdbc.queryForList("SELECT target_id FROM audit_logs WHERE user_id=? AND result='SUCCESS'",
                Long.class, staff.getId());
        assertThat(auditIds).containsExactlyInAnyOrderElementsOf(sample.attempts.stream()
                .filter(a -> a.outcome.equals("SUCCESS")).map(Attempt::id).toList());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND result='FAILURE' AND failure_reason='DUPLICATE_APPOINTMENT'",
                Long.class, staff.getId())).as("Duplicate rejection audit reason").isEqualTo(sample.count("DUPLICATE"));
    }

    void assertSlots(List<AppointmentCreateRequest> requests, int expectedCount) {
        assertThat(storedCount()).isEqualTo(expectedCount);
        assertThat(activeCount()).isEqualTo(expectedCount);
        for (var request : requests) {
            long count = em.createQuery("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id=:doctor AND a.scheduledAt=:slot AND a.status=:status", Long.class)
                    .setParameter("doctor", request.doctor().getId()).setParameter("slot", request.scheduledAt())
                    .setParameter("status", AppointmentStatus.SCHEDULED).getSingleResult();
            assertThat(count).as("Doctor %s slot %s", request.doctor().getId(), request.scheduledAt()).isEqualTo(1);
        }
    }

    String ids() { return fixtureDoctors.stream().map(d -> d.getId().toString()).collect(Collectors.joining(",")); }
    long storedCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id IN (" + ids() + ")", Long.class); }
    long activeCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id IN (" + ids() + ") AND status='SCHEDULED'", Long.class); }
    void clearBookings() {
        if (staff != null) jdbc.update("DELETE FROM audit_logs WHERE user_id=?", staff.getId());
        if (!fixtureDoctors.isEmpty()) jdbc.update("DELETE FROM appointments WHERE doctor_id IN (" + ids() + ")");
    }
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (!cleanupSafe) return;
        clearBookings();
        for (var d : fixtureDoctors) doctors.deleteById(d.getId());
        for (var u : doctorUsers) users.deleteById(u.getId());
        if (patient != null) patients.deleteById(patient.getId());
        if (department != null) departments.deleteById(department.getId());
        if (staff != null) users.deleteById(staff.getId());
    }

    Map<String, Long> globalLockCounters() {
        try {
            Map<String, Long> result = new HashMap<>();
            jdbc.query("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_time','Innodb_row_lock_waits')",
                    (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getString(1), r.getLong(2)));
            return result;
        } catch (RuntimeException e) { return Map.of(); }
    }
    static long delta(Map<String, Long> before, Map<String, Long> after, String key) {
        return before.containsKey(key) && after.containsKey(key) ? after.get(key) - before.get(key) : -1;
    }
    record Attempt(Long id, String outcome, String errorType, long nanos) {}
    record Sample(List<Attempt> attempts, double elapsedMs, long stored, long active,
                  long globalLockMs, long globalLockWaits, int maxPoolPending, int maxPoolActive,
                  int maxDbWaiters, String dbMetricStatus) {
        long count(String outcome) { return attempts.stream().filter(a -> a.outcome.equals(outcome)).count(); }
        double percentile(String outcome, double p) {
            long[] values = attempts.stream().filter(a -> a.outcome.equals(outcome)).mapToLong(Attempt::nanos).sorted().toArray();
            return values.length == 0 ? Double.NaN : values[Math.max(0, (int)Math.ceil(p * values.length) - 1)] / 1_000_000.0;
        }
        String summary() { return "requests=" + attempts.size() + " success=" + count("SUCCESS") + " duplicate=" + count("DUPLICATE")
                + " other=" + count("ERROR") + " stored=" + stored + " active=" + active + " elapsedMs=" + elapsedMs; }
    }

    /** Independent monitoring connection: its queries do not consume the measured Hikari pool. */
    static class Monitor implements AutoCloseable {
        final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
        final HikariDataSource pool;
        Connection connection;
        volatile int maxPending, maxActive, maxDbWaiters = -1;
        volatile String dbMetricStatus = "AVAILABLE";
        Monitor(HikariDataSource pool) {
            this.pool = pool;
            try { connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword()); }
            catch (SQLException e) { dbMetricStatus = e.getSQLState(); }
            sampler.scheduleAtFixedRate(this::sample, 0, 10, TimeUnit.MILLISECONDS);
        }
        void sample() {
            maxPending = Math.max(maxPending, pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
            maxActive = Math.max(maxActive, pool.getHikariPoolMXBean().getActiveConnections());
            if (connection == null || !dbMetricStatus.equals("AVAILABLE")) return;
            try (var s = connection.createStatement(); var r = s.executeQuery("""
                    SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w
                    JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                      AND l.ENGINE=w.ENGINE
                    WHERE l.OBJECT_SCHEMA=DATABASE()
                    """)) { r.next(); maxDbWaiters = Math.max(maxDbWaiters, r.getInt(1)); }
            catch (SQLException e) { dbMetricStatus = "UNAVAILABLE:" + e.getSQLState(); }
        }
        void stop() throws InterruptedException { sampler.shutdown(); if (!sampler.awaitTermination(10, TimeUnit.SECONDS)) sampler.shutdownNow(); }
        public void close() throws Exception { stop(); if (connection != null) connection.close(); }
    }
}
