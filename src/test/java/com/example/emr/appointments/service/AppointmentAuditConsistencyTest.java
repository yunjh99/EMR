package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.global.error.exception.DuplicateAppointmentException;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/** Real MySQL commit/rollback contract tests for success audits. */
@Tag("mysql-audit-consistency")
@Import(AppointmentAuditConsistencyTest.ProbeConfiguration.class)
class AppointmentAuditConsistencyTest extends AppointmentMysqlTestSupport {
    @Autowired DeadlockProbe deadlockProbe;

    @BeforeEach void prepare() throws Exception { fixtures(1); }

    @AfterEach void disableProbe() { deadlockProbe.enabled = false; }

    @Test void committedBookingHasExactlyOneSuccessAudit() throws Exception {
        authenticate();
        Long id = service.createAppointment(requests("A").getFirst());
        assertThat(storedCount()).isEqualTo(1);
        assertThat(successIds()).containsExactly(id);
        assertThat(failureCount()).isZero();
        long orphan = orphanSuccessCount();
        record("COMMIT stored=1 successAudit=1 failureAudit=0 orphanSuccess=" + orphan);
        assertThat(orphan).isZero();
    }

    @Test void duplicateRollbackHasFailureAuditAndNoExtraSuccessAudit() throws Exception {
        authenticate();
        var request = requests("A").getFirst();
        Long id = service.createAppointment(request);
        assertThatThrownBy(() -> service.createAppointment(request)).isInstanceOf(DuplicateAppointmentException.class);
        assertThat(storedCount()).isEqualTo(1);
        assertThat(successIds()).containsExactly(id);
        assertThat(failureCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND result='FAILURE' AND failure_reason='DUPLICATE_APPOINTMENT'",
                Long.class, staff.getId())).isEqualTo(1);
        long orphan = orphanSuccessCount();
        record("DUPLICATE stored=1 successAudit=1 failureAudit=1 orphanSuccess=" + orphan);
        assertThat(orphan).isZero();
    }

    @Test void realDeadlockVictimRollsBackBookingAndRetainsOnlyFailureAudit() throws Exception {
        var base = requests("A").getFirst();
        var a = new AppointmentCreateRequest(base.doctor(), patient, base.scheduledAt(), "deadlock-A");
        var b = new AppointmentCreateRequest(base.doctor(), patient, base.scheduledAt().plusMinutes(30), "deadlock-B");
        deadlockProbe.reset();
        var executor = Executors.newFixedThreadPool(2);
        List<Outcome> outcomes = new ArrayList<>();
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (var request : List.of(a, b)) futures.add(executor.submit(() -> {
                authenticate();
                try { return new Outcome(request.reson(), service.createAppointment(request), null); }
                catch (Throwable error) { return new Outcome(request.reson(), null, error); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            for (var future : futures) outcomes.add(future.get(40, TimeUnit.SECONDS));
        } finally {
            deadlockProbe.enabled = false;
            executor.shutdownNow();
            cleanupSafe = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(cleanupSafe).as("Stop workers before cleanup").isTrue();
        }
        var succeeded = outcomes.stream().filter(o -> o.error == null).toList();
        var failed = outcomes.stream().filter(o -> o.error != null).toList();
        assertThat(succeeded).as(outcomes.toString()).hasSize(1);
        assertThat(failed).as(outcomes.toString()).hasSize(1);
        Throwable root = failed.getFirst().error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        assertThat(root).isInstanceOf(SQLException.class);
        assertThat(((SQLException)root).getErrorCode()).as("Real MySQL deadlock, not a mocked exception").isEqualTo(1213);
        Long rolledBackId = deadlockProbe.insertedIds.get(failed.getFirst().reason);
        assertThat(rolledBackId).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE id=?", Long.class, rolledBackId)).isZero();
        assertThat(storedCount()).isEqualTo(1);
        assertThat(successIds()).containsExactly(succeeded.getFirst().id);
        assertThat(failureCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND result='FAILURE' AND target_id IS NULL",
                Long.class, staff.getId())).isEqualTo(1);
        long orphan = orphanSuccessCount();
        record("DEADLOCK sqlCode=1213 committed=1 rolledBack=1 successAudit=1 failureAudit=1 orphanSuccess=" + orphan);
        assertThat(orphan).isZero();
    }

    @Test void outerTransactionRollbackMustNotLeaveSuccessAudit() throws Exception {
        authenticate();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var id = new AtomicLong();
        transaction.executeWithoutResult(status -> {
            id.set(service.createAppointment(requests("A").getFirst()));
            status.setRollbackOnly(); // business transaction fails after the audited method returns
        });
        assertThat(storedCount()).as("Outer transaction really rolled back").isZero();
        long orphan = orphanSuccessCount();
        record("OUTER_ROLLBACK stored=0 returnedId=" + id.get() + " successAudit=" + successIds().size()
                + " failureAudit=" + failureCount() + " orphanSuccess=" + orphan);
        assertThat(orphan).as("A rolled-back booking must not have a durable SUCCESS audit").isZero();
        assertThat(successIds()).isEmpty();
    }

    @Test void outerTransactionWritesSuccessAuditOnlyAfterCommit() throws Exception {
        authenticate();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var id = new AtomicLong();
        transaction.executeWithoutResult(status -> {
            id.set(service.createAppointment(requests("A").getFirst()));
            assertThat(storedCount()).as("Insert is visible inside the current transaction").isEqualTo(1);
            assertThat(successIds()).as("No SUCCESS before the outer commit").isEmpty();
        });
        assertThat(storedCount()).isEqualTo(1);
        assertThat(successIds()).as("SUCCESS persisted in REQUIRES_NEW after outer commit").containsExactly(id.get());
        assertThat(failureCount()).isZero();
        assertThat(orphanSuccessCount()).isZero();
        record("OUTER_COMMIT beforeCommitSuccess=0 afterCommitSuccess=1 stored=1 orphanSuccess=0");
    }

    List<Long> successIds() {
        return jdbc.queryForList("SELECT target_id FROM audit_logs WHERE user_id=? AND action='CREATE_APPOINTMENT' AND result='SUCCESS'",
                Long.class, staff.getId());
    }
    long failureCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND action='CREATE_APPOINTMENT' AND result='FAILURE'",
                Long.class, staff.getId());
    }
    long orphanSuccessCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_logs l LEFT JOIN appointments a ON a.id=l.target_id
                WHERE l.user_id=? AND l.action='CREATE_APPOINTMENT' AND l.target_table='appointments'
                  AND l.result='SUCCESS' AND a.id IS NULL
                """, Long.class, staff.getId());
    }
    static synchronized void record(String result) throws Exception {
        System.out.println("AUDIT_CONSISTENCY " + result);
        Path file = Path.of("build/reports/appointment-audit-consistency.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, result + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    record Outcome(String reason, Long id, Throwable error) {}

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean DeadlockProbe deadlockProbe(EntityManager em) { return new DeadlockProbe(em); }
    }

    /** Test-only: create an actual two-row lock cycle inside the service's real transaction. */
    @Aspect
    @Order(1)
    static class DeadlockProbe {
        final EntityManager em;
        final Map<String, Long> insertedIds = new ConcurrentHashMap<>();
        volatile CyclicBarrier barrier = new CyclicBarrier(2);
        volatile boolean enabled;
        DeadlockProbe(EntityManager em) { this.em = em; }
        void reset() { insertedIds.clear(); barrier = new CyclicBarrier(2); enabled = true; }

        @Around("bean(appointmentRepository) && execution(* saveAndFlush(..))")
        public Object crossLockAfterInsert(ProceedingJoinPoint point) throws Throwable {
            Object result = point.proceed();
            if (!enabled || !(result instanceof Appointment appointment)
                    || !appointment.getReason().startsWith("deadlock-")) return result;
            insertedIds.put(appointment.getReason(), appointment.getId());
            barrier.await(15, TimeUnit.SECONDS);
            Long other = insertedIds.entrySet().stream().filter(e -> !e.getKey().equals(appointment.getReason()))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            em.createNativeQuery("UPDATE appointments SET reason=CONCAT(reason, '-probe') WHERE id=:other")
                    .setParameter("other", other).executeUpdate();
            return result;
        }
    }
}
