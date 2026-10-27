package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.appointments.domain.AppointmentConstraintViolations;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.appointments.repository.AppointmentRepository;
import com.example.emr.patients.domain.Patient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Tag("mysql-correctness")
class AppointmentUniqueCorrectnessTest extends AppointmentMysqlTestSupport {
    @Autowired AppointmentRepository appointments;

    @BeforeEach
    void prepare() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='appointments'
                  AND index_name='uk_active_doctor_slot' AND non_unique=0
                """, Long.class)).as("Migration must be applied first").isEqualTo(2);
        fixtures(100);
    }

    @Test void A_sameDoctorSameSlot() throws Exception { verify("A", 1, 99); }
    @Test void B_sameDoctorDifferentSlotsAcrossDates() throws Exception { verify("B", 100, 0); }
    @Test void C_differentDoctorsSameSlot() throws Exception { verify("C", 100, 0); }

    void verify(String scenario, int successes, int duplicates) throws Exception {
        var requests = requests(scenario);
        var result = execute(requests, service::createAppointment, false);
        record(scenario + " " + result.summary());
        assertThat(result.count("SUCCESS")).isEqualTo(successes);
        assertThat(result.count("DUPLICATE")).isEqualTo(duplicates);
        assertThat(result.count("ERROR")).as(result.attempts().toString()).isZero();
        assertSlots(requests, successes);
    }

    @Test void D_cancelledHistoryRemainsAndSlotCanBeBookedAgain() throws Exception {
        authenticate();
        var request = requests("A").getFirst();
        long first = service.createAppointment(request);
        // Test-only transition: no cancellation API exists in the current project.
        jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE id=?", first);
        long second = service.createAppointment(request);
        jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE id=?", second);
        long third = service.createAppointment(request);
        assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
        assertThat(storedCount()).isEqualTo(3);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND status='CANCELLED' AND active_slot_at IS NULL",
                Long.class, request.doctor().getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND result='SUCCESS'",
                Long.class, staff.getId())).isEqualTo(3);
        record("D success=3 cancelledHistory=2 active=1 auditSuccess=3 PASS");
    }

    @Test void completedHistoryDoesNotOccupyActiveSlot() throws Exception {
        authenticate();
        var request = requests("A").getFirst();
        long first = service.createAppointment(request);
        jdbc.update("UPDATE appointments SET status='COMPLETED' WHERE id=?", first);
        assertThat(service.createAppointment(request)).isNotEqualTo(first);
        assertThat(storedCount()).isEqualTo(2);
        assertThat(activeCount()).isEqualTo(1);
        record("COMPLETED history=1 active=1 PASS");
    }

    @Test void uniqueViolationRollsBackEntireTransaction() throws Exception {
        var request = requests("A").getFirst();
        var transaction = new TransactionTemplate(transactionManager);
        Throwable failure = catchThrowable(() -> transaction.executeWithoutResult(status -> {
            appointments.saveAndFlush(new Appointment(request.doctor(), patient, request.scheduledAt(), "first write"));
            appointments.saveAndFlush(new Appointment(request.doctor(), patient, request.scheduledAt(), "duplicate"));
        }));
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(AppointmentConstraintViolations.isActiveSlotDuplicate(failure)).isTrue();
        assertThat(storedCount()).as("Both writes rolled back").isZero();
        record("ROLLBACK uniqueViolation=true writes=2 committed=0 PASS");
    }

    @Test void realForeignKeyErrorIsNotAnAppointmentDuplicate() throws Exception {
        authenticate();
        var request = requests("A").getFirst();
        var missingPatient = em.getReference(Patient.class, Long.MAX_VALUE);
        Throwable failure = catchThrowable(() -> service.createAppointment(new AppointmentCreateRequest(
                request.doctor(), missingPatient, request.scheduledAt(), "FK negative test")));
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(AppointmentConstraintViolations.isActiveSlotDuplicate(failure)).isFalse();
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        assertThat(root).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException)root).getErrorCode()).isEqualTo(1452);
        assertThat(storedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND result='FAILURE'",
                Long.class, staff.getId())).isEqualTo(1);
        record("FK sqlCode=1452 duplicate=false committed=0 failureAudit=1 PASS");
    }

    static synchronized void record(String line) throws Exception {
        System.out.println("UNIQUE_CORRECTNESS " + line);
        Path file = Path.of("build/reports/appointment-unique-correctness.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
