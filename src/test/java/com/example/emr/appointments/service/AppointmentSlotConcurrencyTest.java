package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.AppointmentSlot;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Current production service unchanged; schema cloned from its current MySQL database. */
@Tag("mysql-slot-concurrency")
class AppointmentSlotConcurrencyTest extends AppointmentMysqlTestSupport {
    @BeforeEach
    void prepareSlots() throws Exception {
        try (var c = dataSource.getConnection()) {
            assertThat(c.getCatalog()).startsWith("emr_bench_slot_");
        }
        fixtures(100);
        Map<String, AppointmentCreateRequest> slots = new LinkedHashMap<>();
        for (String scenario : List.of("A", "B", "C")) {
            for (var r : requests(scenario)) slots.putIfAbsent(r.doctor().getId() + "/" + r.scheduledAt(), r);
        }
        // Populate exactly one row per slot using the same Hibernate datetime binding as the service.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (var r : slots.values()) {
                var slot = BeanUtils.instantiateClass(AppointmentSlot.class);
                ReflectionTestUtils.setField(slot, "doctorId", r.doctor().getId());
                ReflectionTestUtils.setField(slot, "slotAt", r.scheduledAt());
                em.persist(slot);
            }
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slots WHERE doctor_id IN (" + ids() + ")",
                Long.class)).isEqualTo(slots.size());
    }

    @AfterEach
    void removeFixtureSlots() {
        if (cleanupSafe && !fixtureDoctors.isEmpty())
            jdbc.update("DELETE FROM appointment_slots WHERE doctor_id IN (" + ids() + ")");
    }

    @Test
    void repeatCurrentSlotLockImplementation() throws Exception {
        Path directory = Path.of("build/reports/appointment-slot-concurrency");
        Files.createDirectories(directory);
        Path csv = directory.resolve("results.csv");
        Files.writeString(csv, "scenario,phase,round,requests,success,duplicate,other,stored,active,elapsedMs,errorTypes\n");
        var pool = dataSource.unwrap(HikariDataSource.class);
        Files.writeString(directory.resolve("environment.txt"), "threads=100 poolMax=" + pool.getMaximumPoolSize()
                + " poolIsolation=" + pool.getTransactionIsolation() + " serviceIsolation=READ_COMMITTED\n"
                + "protection=slot_row_PESSIMISTIC_WRITE; production_service_unchanged\n"
                + "appointmentsDDL=" + jdbc.queryForMap("SHOW CREATE TABLE appointments") + "\n"
                + "slotsDDL=" + jdbc.queryForMap("SHOW CREATE TABLE appointment_slots") + "\n");
        List<String> violations = new ArrayList<>();
        for (int round = -2; round < 5; round++) {
            String phase = round < 0 ? "WARMUP" : "MEASURE";
            for (String scenario : List.of("A", "B", "C")) {
                clearBookings();
                var requestList = requests(scenario);
                var sample = execute(requestList, service::createAppointment, false);
                String errorTypes = sample.attempts().stream().filter(a -> a.outcome().equals("ERROR"))
                        .map(Attempt::errorType).distinct().sorted().reduce((a, b) -> a + "|" + b).orElse("");
                Files.writeString(csv, String.format(Locale.ROOT, "%s,%s,%d,100,%d,%d,%d,%d,%d,%.6f,%s%n",
                        scenario, phase, round, sample.count("SUCCESS"), sample.count("DUPLICATE"), sample.count("ERROR"),
                        sample.stored(), sample.active(), sample.elapsedMs(), errorTypes), StandardOpenOption.APPEND);
                System.out.println("SLOT_CONCURRENCY " + scenario + " " + phase + " " + round + " " + sample.summary());
                int expected = scenario.equals("A") ? 1 : 100;
                if (sample.count("SUCCESS") != expected || sample.count("DUPLICATE") != 100 - expected
                        || sample.count("ERROR") != 0 || sample.stored() != expected || sample.active() != expected)
                    violations.add(scenario + "/" + phase + "/" + round + " " + sample.summary());
                try { assertSlots(requestList, expected); }
                catch (AssertionError error) { violations.add(scenario + "/" + phase + "/" + round + " per-slot count mismatch"); }
            }
        }
        assertThat(violations).as("All samples recorded; no production logic modified to make this test pass").isEmpty();
    }
}
