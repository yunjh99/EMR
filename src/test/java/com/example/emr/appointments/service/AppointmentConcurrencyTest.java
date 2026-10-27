package com.example.emr.appointments.service;

import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.appointments.domain.AppointmentStatus;
import com.example.emr.global.error.exception.DuplicateAppointmentException;
import jakarta.persistence.EntityManager;
import com.example.emr.departments.domain.Department;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.doctors.repository.DoctorRepository;
import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.repository.PatientRepository;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

// 실제 MySQL만 사용한다. 테스트 메서드/클래스에 @Transactional을 붙이지 않는다.
@SpringBootTest
@TestPropertySource(locations = {
        "file:src/main/resources/application.properties",
        "classpath:application-appointment-concurrency.properties"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("mysql-concurrency")
class AppointmentConcurrencyTest {
    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 100;

    @Autowired AppointmentService service;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;

    private User staff;
    private User doctorUser;
    private Department department;
    private Doctor doctor;
    private Patient patient;
    private LocalDateTime scheduledAt;
    private String schema;
    private boolean cleanupSafe = true;

    @DynamicPropertySource
    static void mysqlOverrides(DynamicPropertyRegistry registry) {
        Map.of("APPOINTMENT_TEST_MYSQL_URL", "spring.datasource.url",
                "APPOINTMENT_TEST_MYSQL_USERNAME", "spring.datasource.username",
                "APPOINTMENT_TEST_MYSQL_PASSWORD", "spring.datasource.password")
                .forEach((env, property) -> {
                    String value = System.getenv(env);
                    if (value != null) registry.add(property, () -> value);
                });
    }

    @BeforeEach
    void prepareCommittedFixtures() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .as("H2 결과로 대체하지 않음").isEqualTo("MySQL");
            schema = "MySQL=" + connection.getMetaData().getDatabaseProductVersion()
                    + ", isolation=" + connection.getTransactionIsolation() + " (변경하지 않음)\n";
        }
        // 실제 DB의 FK, UNIQUE, NOT NULL, enum, 엔진을 출력한다. DDL은 실행하지 않는다.
        for (String table : List.of("appointments", "doctors", "patients", "departments", "users", "audit_logs")) {
            schema += jdbc.queryForMap("SHOW CREATE TABLE " + table).values().stream()
                    .map(String::valueOf).collect(Collectors.joining("\n")) + "\n";
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        staff = users.saveAndFlush(new User("ac_s_" + suffix, "test-hash", "예약 테스트 직원", Role.STAFF));
        doctorUser = users.saveAndFlush(new User("ac_d_" + suffix, "test-hash", "예약 테스트 의사", Role.DOCTOR));
        department = departments.saveAndFlush(new Department("AC_" + suffix, "예약 동시성 테스트"));
        doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "AC_" + suffix, "내과"));
        patient = patients.saveAndFlush(new Patient("AC_" + suffix, "예약 테스트 환자",
                "010-0000-0000", "테스트 주소", Gender.F, LocalDate.of(1990, 1, 1)));
        scheduledAt = LocalDate.now().plusDays(1).atTime(9, 0);
        assertThat(slotCount(false)).as("테스트 전 슬롯 비어 있음").isZero();
    }

    @Test
    @DisplayName("A: 동일 슬롯 100개 요청의 실제 결과 관찰")
    void observeCurrentBehavior() throws Exception {
        Result result = runRequests("A-observation");
        // 중복이나 요청 예외 자체로 실패시키지 않는다. 실행 누락은 테스트 장치 오류다.
        assertThat(result.successIds().size() + result.failures().size()).isEqualTo(REQUEST_COUNT);
    }

    @Test
    @DisplayName("B: 동일 의사·시간의 활성 예약은 최대 1건")
    void activeSlotMustContainAtMostOneAppointment() throws Exception {
        Result result = runRequests("B-requirement");
        assertSoftly(check -> {
            check.assertThat(result.activeCount()).as("동일 슬롯 활성(SCHEDULED) 예약 수").isLessThanOrEqualTo(1);
            check.assertThat(result.successIds()).as("전부 실패한 실행은 중복 방지 성공이 아님").isNotEmpty();
            // 명시적인 업무 중복 거절만 허용한다. DB/인증 오류는 정상 거절이 아니다.
            check.assertThat(result.failures()).filteredOn(failure -> !isDuplicateRejection(failure))
                    .as("중복 거절 외 DB/인증 등 요청 오류").isEmpty();
            check.assertThat(result.storedCount()).as("반환 성공과 커밋 건수 일치")
                    .isEqualTo(result.successIds().size());
            check.assertThat(result.auditSuccessIds()).as("AOP가 삼킨 감사 로그 오류/누락 확인")
                    .containsExactlyInAnyOrderElementsOf(result.successIds());
            check.assertThat(result.auditCount()).as("모든 요청의 감사 로그 기록")
                    .isEqualTo(REQUEST_COUNT);
        });
    }

    private Result runRequests(String scenario) throws Exception {
        var executor = Executors.newFixedThreadPool(THREAD_COUNT);
        var ready = new CountDownLatch(REQUEST_COUNT);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(REQUEST_COUNT);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                                new LoginUser(staff.getId(), Role.STAFF), null,
                                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
                        SecurityContextHolder.setContext(context);
                        ready.countDown();
                        if (!start.await(30, TimeUnit.SECONDS)) throw new TimeoutException("시작 신호 대기 초과");
                        // 서비스 프록시로 @PreAuthorize와 @Audit를 그대로 실행한다.
                        // 실제 서비스/Repository 트랜잭션 설정 사용. 바깥 트랜잭션은 추가하지 않는다.
                        return service.createAppointment(new AppointmentCreateRequest(
                                doctor, patient, scheduledAt, "예약 동시성 테스트"));
                    } finally {
                        SecurityContextHolder.clearContext();
                        done.countDown();
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("100개 작업 시작 준비").isTrue();
            long started = System.nanoTime();
            start.countDown();
            if (!done.await(120, TimeUnit.SECONDS)) {
                throw new TimeoutException("요청 완료 제한 120초 초과, 미완료=" + done.getCount()
                        + ", doctorId=" + doctor.getId() + "; 중복 방지 성공으로 해석할 수 없음");
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            List<Long> ids = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    ids.add(future.get());
                } catch (ExecutionException exception) {
                    Throwable error = exception.getCause();
                    Throwable root = error;
                    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                    String sql = root instanceof SQLException e
                            ? " SQLState=" + e.getSQLState() + " vendorCode=" + e.getErrorCode() : "";
                    failures.add(error.getClass().getName() + " -> " + root.getClass().getName() + sql);
                }
            }
            var auditIds = jdbc.queryForList("""
                    SELECT target_id FROM audit_logs
                    WHERE user_id = ? AND action = 'CREATE_APPOINTMENT' AND result = 'SUCCESS'
                    """, Long.class, staff.getId());
            long auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ?",
                    Long.class, staff.getId());
            long storedCount = jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id = ?",
                    Long.class, doctor.getId());
            var databaseSlots = jdbc.queryForList("""
                    SELECT scheduled_at, status, COUNT(*) AS booking_count
                    FROM appointments WHERE doctor_id = ? GROUP BY scheduled_at, status
                    """, doctor.getId());
            var result = new Result(ids, failures, storedCount, slotCount(true), auditIds, auditCount);
            var failureCounts = failures.stream().collect(Collectors.groupingBy(s -> s, TreeMap::new, Collectors.counting()));
            int poolSize = dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
            String report = """
                    scenario=%s
                    doctorId=%d patientId=%d scheduledAt=%s
                    requests=%d success=%d failure=%d
                    duplicateRejections=%d unexpectedFailures=%d
                    failureTypes=%s
                    storedAppointments=%d activeSlotAppointments=%d
                    databaseSlotGroups=%s
                    activeDefinition=status = 'SCHEDULED'
                    auditSuccess=%d auditTotal=%d missingSuccessAudit=%d
                    elapsedMs=%d (시작 신호부터 모든 서비스/AOP 반환까지; 준비/정리 제외)
                    workerThreads=%d dbMaxPoolSize=%d
                    시작 신호는 함께 전달되지만 실제 DB 동시 실행은 커넥션 풀과 DB 스케줄링에 제한됨.
                    트랜잭션은 실제 서비스/Repository 설정을 따름. 테스트에서 외부 트랜잭션을 추가하지 않음.
                    Audit.write는 REQUIRES_NEW이며 추가 DB 작업 발생. AOP는 감사 저장 오류를 삼킴.
                    DuplicateAppointmentException만 정상 중복 거절로 분류. DB/인증 오류는 별도 오류.
                    HTTP/JWT 필터 부하 테스트는 아님.
                    %s
                    """.formatted(scenario, doctor.getId(), patient.getId(), scheduledAt,
                    REQUEST_COUNT, ids.size(), failures.size(),
                    failures.stream().filter(AppointmentConcurrencyTest::isDuplicateRejection).count(),
                    failures.stream().filter(failure -> !isDuplicateRejection(failure)).count(), failureCounts,
                    result.storedCount(), result.activeCount(), databaseSlots, auditIds.size(), auditCount,
                    ids.stream().filter(id -> !auditIds.contains(id)).count(), elapsedMs, THREAD_COUNT, poolSize, schema);
            System.out.println(report);
            Path reportDir = Path.of("build", "reports", "appointment-concurrency");
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve(scenario + ".txt"), report);
            return result;
        } finally {
            start.countDown();
            executor.shutdownNow();
            cleanupSafe = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(cleanupSafe).as("작업 미종료 시 데이터 정리 중단; doctorId=" + doctor.getId()).isTrue();
        }
    }

    private static boolean isDuplicateRejection(String failure) {
        return failure.startsWith(DuplicateAppointmentException.class.getName() + " -> ");
    }

    private long slotCount(boolean activeOnly) {
        // 저장과 조회에 동일한 Hibernate 시간 바인딩을 사용한다.
        // JDBC의 LocalDateTime 직접 바인딩은 서버/세션 시간대에 따라 다른 슬롯을 조회할 수 있다.
        var query = entityManager.createQuery("""
                SELECT COUNT(a) FROM Appointment a
                WHERE a.doctor.id = :doctorId AND a.scheduledAt = :scheduledAt
                """ + (activeOnly ? " AND a.status = :status" : ""), Long.class)
                .setParameter("doctorId", doctor.getId())
                .setParameter("scheduledAt", scheduledAt);
        if (activeOnly) query.setParameter("status", AppointmentStatus.SCHEDULED);
        return query.getSingleResult();
    }

    @AfterEach
    void removeOnlyThisTestsData() {
        if (!cleanupSafe) return;
        // 테스트별 고유 fixture ID로 한정한다. 기존 데이터/제약조건은 변경하지 않는다.
        if (staff != null) jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", staff.getId());
        if (doctor != null) {
            jdbc.update("DELETE FROM appointments WHERE doctor_id = ?", doctor.getId());
            doctors.deleteById(doctor.getId());
        }
        if (patient != null) patients.deleteById(patient.getId());
        if (department != null) departments.deleteById(department.getId());
        if (doctorUser != null) users.deleteById(doctorUser.getId());
        if (staff != null) users.deleteById(staff.getId());
    }

    private record Result(List<Long> successIds, List<String> failures, long storedCount, long activeCount,
                          List<Long> auditSuccessIds, long auditCount) {}
}
