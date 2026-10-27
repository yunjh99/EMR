package com.example.emr.encounters.service;

import com.example.emr.appointments.service.AppointmentService;
import com.example.emr.appointments.domain.Appointment;
import com.example.emr.audit_logs.aspect.AuditAspect;
import com.example.emr.audit_logs.repository.AuditLogRepository;
import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.encounter_histories.domain.EncounterHistory;
import com.example.emr.encounter_histories.repository.EncounterHistoryRepository;
import com.example.emr.encounters.controller.EncounterController;
import com.example.emr.encounters.dto.*;
import com.example.emr.encounters.repository.EncounterRepository;
import com.example.emr.global.error.ApiExceptionHandler;
import com.example.emr.global.config.SecurityConfig;
import com.example.emr.global.filter.ApiAuthenticationEntryPoint;
import com.example.emr.global.filter.ApiAccessDeniedHandler;
import com.example.emr.global.security.JwtProvider;
import com.example.emr.global.error.exception.*;
import com.example.emr.patients.domain.*;
import com.example.emr.patients.repository.PatientRepository;
import com.example.emr.users.domain.*;
import com.example.emr.users.domain.Role;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import com.example.emr.visits.domain.*;
import com.example.emr.visits.repository.VisitRepository;
import com.example.emr.visits.service.VisitService;
import com.example.emr.visits.controller.VisitController;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/** Real MySQL/JPA, production transaction/security/audit proxies; no outer test transaction. */
@SpringBootTest(classes = EncounterHistoryMysqlTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(locations = "file:src/main/resources/application.properties", properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false",
        "spring.datasource.hikari.maximum-pool-size=12",
        "logging.level.org.hibernate.orm.jdbc.error=OFF"})
class EncounterHistoryMysqlTest {
    @Autowired EncounterService service;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EncounterRepository encounters;
    @Autowired VisitRepository visitRepository;
    @Autowired VisitService visitService;
    @Autowired WebApplicationContext webContext;
    @Autowired JwtProvider jwt;
    MockMvc mvc;
    MockMvc secureMvc;
    User owner, otherDoctor, nurse, staff, admin;
    Patient patient;
    Doctor doctor;
    Department department;
    Long encounterId, visitId;
    static long legacyEncounterId;
    static boolean prepared;

    @DynamicPropertySource
    static synchronized void mysql(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("ENCOUNTER_TEST_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/emr_bench_encounter_[a-zA-Z0-9_]+(?:\\?.*)?"))
            throw new IllegalStateException("Set ENCOUNTER_TEST_MYSQL_URL to a fresh isolated emr_bench_encounter_ schema");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) { properties.load(reader); }
        String username = setting(properties, "username");
        String password = setting(properties, "password");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        if (!prepared) {
            try (var connection = DriverManager.getConnection(url, username, password)) {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
                apply(connection, "db/visits_encounters.sql");
                long user = insert(connection, "INSERT INTO users(username,password_hash,name,role,is_active,created_at) VALUES('history_migration','hash','migration','DOCTOR',true,NOW())");
                long dept = insert(connection, "INSERT INTO departments(code,name,is_active,created_at) VALUES('HISTORY_MIGRATION','migration',true,NOW())");
                long patient = insert(connection, "INSERT INTO patients(patient_no,name,birth_date,gender,phone,address,is_active,created_at) VALUES('HISTORY_MIGRATION','migration','1990-01-01','F','010','test',true,NOW())");
                long doctor = insert(connection, "INSERT INTO doctors(user_id,department_id,license_no,is_active,created_at) VALUES(?,?,'HISTORY_MIGRATION',true,NOW())", user, dept);
                long visit = insert(connection, "INSERT INTO visits(patient_id,department_id,doctor_id,visit_type,status) VALUES(?,?,?,'WALK_IN','COMPLETED')", patient, dept, doctor);
                legacyEncounterId = insert(connection, "INSERT INTO encounters(visit_id,chief_complaint,clinical_note,treatment_plan) VALUES(?,'legacy complaint','legacy note','legacy plan')", visit);
                apply(connection, "db/encounter_histories.sql");
                System.out.println("ENCOUNTER_HISTORY_ENV MySQL=" + connection.getMetaData().getDatabaseProductVersion()
                        + " schema=" + connection.getCatalog() + " hikariMax=12 concurrencyWorkers=10");
            }
            prepared = true;
        }
    }

    static String setting(Properties properties, String key) {
        String value = System.getenv("ENCOUNTER_TEST_MYSQL_" + key.toUpperCase());
        if (value == null) value = properties.getProperty("spring.datasource." + key);
        if (value.startsWith("${") && value.endsWith("}")) {
            String[] parts = value.substring(2, value.length() - 1).split(":", 2);
            value = System.getenv(parts[0]);
            if (value == null && parts.length == 2) value = parts[1];
        }
        return Objects.requireNonNull(value, "Missing MySQL " + key);
    }

    static void apply(Connection connection, String file) throws Exception {
        for (String sql : Files.readString(Path.of(file)).replaceAll("(?m)^--.*$", "").split(";"))
            if (!sql.isBlank()) try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (var result = statement.getGeneratedKeys()) { result.next(); return result.getLong(1); }
        }
    }

    @BeforeEach
    void fixtures() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        owner = users.saveAndFlush(new User("eh_o_" + key, "hash", "담당 의사", Role.DOCTOR));
        otherDoctor = users.saveAndFlush(new User("eh_d_" + key, "hash", "다른 의사", Role.DOCTOR));
        nurse = users.saveAndFlush(new User("eh_n_" + key, "hash", "간호사", Role.NURSE));
        staff = users.saveAndFlush(new User("eh_s_" + key, "hash", "직원", Role.STAFF));
        admin = users.saveAndFlush(new User("eh_a_" + key, "hash", "관리자", Role.ADMIN));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            department = new Department("EH_" + key, "이력 테스트"); em.persist(department);
            patient = new Patient("EH_" + key, "환자", "010", "테스트", Gender.F, LocalDate.of(1990, 1, 1)); em.persist(patient);
            doctor = new Doctor(owner, department, "EH_" + key, null); em.persist(doctor);
        });
        authenticate(owner);
        encounterId = createRecord();
        visitId = service.getEncounter(encounterId).visitId();
        mvc = MockMvcBuilders.standaloneSetup(new EncounterController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(), new PageableHandlerMethodArgumentResolver()).build();
        secureMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    long createRecord() {
        long id = new TransactionTemplate(transactionManager).execute(tx -> {
            Visit visit = new Visit(patient, null, department, doctor, VisitType.WALK_IN);
            visit.start(); em.persist(visit); em.flush(); return visit.getId();
        });
        return service.createEncounter(new EncounterCreateRequest(id, "original complaint", "original note", "original plan"), login(owner));
    }

    void authenticate(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(login(user), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        SecurityContextHolder.setContext(context);
    }
    LoginUser login(User user) { return new LoginUser(user.getId(), user.getRole()); }
    EncounterUpdateRequest change(String note) { return new EncounterUpdateRequest(null, note, null, "소견 정정"); }
    long version() { return jdbc.queryForObject("SELECT version FROM encounters WHERE id=?", Long.class, encounterId); }
    long historyCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM encounter_histories WHERE encounter_id=?", Long.class, encounterId); }
    long auditCount(String result) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_table='encounters' AND target_id=? AND action='UPDATE_ENCOUNTER' AND result=?", Long.class, encounterId, result);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (owner == null) return;
        jdbc.update("DELETE FROM encounter_histories WHERE encounter_id IN (SELECT e.id FROM encounters e JOIN visits v ON e.visit_id=v.id WHERE v.patient_id=?)", patient.getId());
        jdbc.update("DELETE FROM encounters WHERE visit_id IN (SELECT id FROM visits WHERE patient_id=?)", patient.getId());
        jdbc.update("DELETE FROM visits WHERE patient_id=?", patient.getId());
        jdbc.update("DELETE FROM appointments WHERE patient_id=?", patient.getId());
        jdbc.update("DELETE FROM audit_logs WHERE user_id IN (?,?,?,?,?)", owner.getId(), otherDoctor.getId(), nurse.getId(), staff.getId(), admin.getId());
        jdbc.update("DELETE FROM doctors WHERE id=?", doctor.getId());
        jdbc.update("DELETE FROM patients WHERE id=?", patient.getId());
        jdbc.update("DELETE FROM departments WHERE id=?", department.getId());
        for (User user : List.of(owner, otherDoctor, nurse, staff, admin)) users.deleteById(user.getId());
    }

    @Test
    void migrationInitializesExistingRecordWithoutChangingContents() {
        assertThat(jdbc.queryForObject("SELECT version FROM encounters WHERE id=?", Long.class, legacyEncounterId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT clinical_note FROM encounters WHERE id=?", String.class, legacyEncounterId)).isEqualTo("legacy note");
    }

    @Test
    void creationStartsAtOneCompletesVisitAndAuditsCommittedId() {
        assertThat(version()).isEqualTo(1);
        assertThat(historyCount()).isZero();
        assertThat(service.getEncounter(encounterId).visitStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CREATE_ENCOUNTER' AND target_id=? AND result='SUCCESS'", Long.class, encounterId)).isEqualTo(1);
    }

    @Test
    void firstAndSecondEditsPreserveWholePreviousVersionsAndNullFields() {
        service.updateEncounter(encounterId, change("note two"), login(owner));
        assertThat(version()).isEqualTo(2);
        var first = service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent().getFirst();
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.chiefComplaint()).isEqualTo("original complaint");
        assertThat(first.clinicalNote()).isEqualTo("original note");
        assertThat(first.treatmentPlan()).isEqualTo("original plan");
        assertThat(first.changedBy()).isEqualTo(owner.getId());
        assertThat(first.changeReason()).isEqualTo("소견 정정");
        assertThat(first.changedAt()).isNotNull();
        service.updateEncounter(encounterId, new EncounterUpdateRequest("complaint three", null, null, "주호소 정정"), login(owner));
        assertThat(version()).isEqualTo(3);
        var histories = service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent();
        assertThat(histories).extracting(h -> h.version()).containsExactly(1L, 2L);
        assertThat(histories.get(1).clinicalNote()).isEqualTo("note two");
        var latest = service.getEncounter(encounterId);
        assertThat(latest.chiefComplaint()).isEqualTo("complaint three");
        assertThat(latest.clinicalNote()).isEqualTo("note two");
        assertThat(latest.treatmentPlan()).isEqualTo("original plan");
        assertThat(latest.visitStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(auditCount("SUCCESS")).isEqualTo(2);
    }

    @Test
    void nullOrIdenticalValuesDoNotProduceHistoryOrNewVersion() {
        service.updateEncounter(encounterId, new EncounterUpdateRequest(null, null, null, "내용 확인"), login(owner));
        service.updateEncounter(encounterId, new EncounterUpdateRequest("original complaint", "original note", "original plan", "내용 확인"), login(owner));
        assertThat(version()).isEqualTo(1);
        assertThat(historyCount()).isZero();
        assertThat(service.getEncounter(encounterId).updatedAt()).isNull();
    }

    @Test
    void anotherDoctorCannotEdit() {
        authenticate(otherDoctor);
        assertThatThrownBy(() -> service.updateEncounter(encounterId, change("denied"), login(otherDoctor))).isInstanceOf(AccessDeniedException.class);
        assertThat(version()).isEqualTo(1);
        assertThat(historyCount()).isZero();
        assertThat(auditCount("SUCCESS")).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"NURSE", "STAFF", "ADMIN"})
    void nonDoctorsCannotEdit(Role role) {
        User actor = role == Role.NURSE ? nurse : role == Role.STAFF ? staff : admin;
        authenticate(actor);
        assertThatThrownBy(() -> service.updateEncounter(encounterId, change("denied"), login(actor))).isInstanceOf(AccessDeniedException.class);
        assertThat(historyCount()).isZero();
        assertThat(version()).isEqualTo(1);
    }

    @Test
    void controllerRejectsMissingBlankAndOversizedCorrectionReasons() throws Exception {
        for (String body : List.of("{\"clinicalNote\":\"changed\"}",
                "{\"changeReason\":null}", "{\"changeReason\":\"   \"}",
                "{\"changeReason\":\"" + "x".repeat(501) + "\"}")) {
            mvc.perform(patch("/api/encounters/{id}", encounterId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(historyCount()).isZero();
        assertThat(version()).isEqualTo(1);
    }

    @Test
    void directServiceCallsAlsoRequireCorrectionReason() {
        assertThatThrownBy(() -> service.updateEncounter(encounterId,
                new EncounterUpdateRequest(null, "changed", null, " "), login(owner)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(historyCount()).isZero();
        assertThat(version()).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateHistoricalVersion() {
        service.updateEncounter(encounterId, change("changed"), login(owner));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO encounter_histories(encounter_id,version,chief_complaint,clinical_note,
                treatment_plan,changed_by,change_reason,changed_at)
                SELECT encounter_id,version,chief_complaint,clinical_note,treatment_plan,changed_by,change_reason,changed_at
                FROM encounter_histories WHERE encounter_id=?
                """, encounterId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(historyCount()).isEqualTo(1);
        assertThat(version()).isEqualTo(2);
    }

    @Test
    void validPatchUsesAuthenticatedUserAndKeepsVisitCompleted() throws Exception {
        mvc.perform(patch("/api/encounters/{id}", encounterId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clinicalNote\":\"HTTP edit\",\"changeReason\":\"정정\"}"))
                .andExpect(status().isOk());
        assertThat(version()).isEqualTo(2);
        assertThat(historyCount()).isEqualTo(1);
        assertThat(service.getEncounter(encounterId).visitStatus()).isEqualTo(VisitStatus.COMPLETED);
    }

    @Test
    void commitFailureRollsBackHistoryContentAndVersionWithoutSuccessAudit() {
        jdbc.execute("""
                CREATE TRIGGER test_encounter_history_failure BEFORE UPDATE ON encounters FOR EACH ROW
                BEGIN
                  IF NEW.id = %d AND NEW.clinical_note = 'force failure' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test-only commit failure';
                  END IF;
                END
                """.formatted(encounterId));
        try {
            assertThatThrownBy(() -> service.updateEncounter(encounterId, change("force failure"), login(owner))).isInstanceOf(RuntimeException.class);
            assertThat(historyCount()).isZero();
            assertThat(version()).isEqualTo(1);
            assertThat(service.getEncounter(encounterId).clinicalNote()).isEqualTo("original note");
            assertThat(auditCount("SUCCESS")).isZero();
            assertThat(auditCount("FAILURE")).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER test_encounter_history_failure");
        }
    }

    @Test
    void outerTransactionRollbackAlsoLeavesNoSuccessAudit() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.updateEncounter(encounterId, change("rolled back"), login(owner));
            assertThat(auditCount("SUCCESS")).isZero();
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(historyCount()).isZero();
        assertThat(version()).isEqualTo(1);
        assertThat(service.getEncounter(encounterId).clinicalNote()).isEqualTo("original note");
        assertThat(auditCount("SUCCESS")).isZero();
    }

    @RepeatedTest(3)
    void concurrentEditsKeepEveryVersionWithoutDuplicates() throws Exception {
        int count = 10;
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int request = i;
                futures.add(executor.submit(() -> {
                    authenticate(owner); ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        service.updateEncounter(encounterId, change("concurrent " + request), login(owner));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(version()).isEqualTo(11);
        assertThat(historyCount()).isEqualTo(10);
        var history = service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent();
        assertThat(history).extracting(h -> h.version()).containsExactly(1L,2L,3L,4L,5L,6L,7L,8L,9L,10L);
        Set<String> notes = new HashSet<>();
        history.forEach(h -> notes.add(h.clinicalNote()));
        notes.add(service.getEncounter(encounterId).clinicalNote());
        assertThat(notes).hasSize(11).contains("original note");
        for (int i = 0; i < count; i++) assertThat(notes).contains("concurrent " + i);
        assertThat(auditCount("SUCCESS")).isEqualTo(10);
        assertThat(auditCount("FAILURE")).isZero();
        assertThat(service.getEncounter(encounterId).visitStatus()).isEqualTo(VisitStatus.COMPLETED);
        System.out.println("ENCOUNTER_HISTORY_CONCURRENCY requests=10 success=10 failure=0 histories=10 latestVersion=11 visit=COMPLETED");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "NURSE"})
    void permittedReadersSeeSortedFullSnapshotsAndCannotCrossEncounterBoundary(Role role) throws Exception {
        service.updateEncounter(encounterId, change("note two"), login(owner));
        service.updateEncounter(encounterId, change("note three"), login(owner));
        long otherEncounter = createRecord();
        var history = service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent().getFirst();
        authenticate(role == Role.NURSE ? nurse : otherDoctor);
        mvc.perform(get("/api/encounters/{id}/histories", encounterId).param("size", "1").param("page", "0").param("sort", "version,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.content[0].version").value(1));
        mvc.perform(get("/api/encounters/{id}/histories", encounterId).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].version").value(2));
        mvc.perform(get("/api/encounters/{id}/histories/{history}", encounterId, history.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clinicalNote").value("original note"))
                .andExpect(jsonPath("$.data.chiefComplaint").value("original complaint"))
                .andExpect(jsonPath("$.data.treatmentPlan").value("original plan"))
                .andExpect(jsonPath("$.data.changedBy").value(owner.getId()));
        mvc.perform(get("/api/encounters/{id}/histories/{history}", otherEncounter, history.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/encounters/{id}/histories", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STAFF", "ADMIN"})
    void otherRolesCannotReadHistories(Role role) {
        authenticate(role == Role.STAFF ? staff : admin);
        assertThatThrownBy(() -> service.getEncounterHistories(encounterId, PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getEncounterHistory(encounterId, 1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingEncounterOnEditIs404AndDuplicateCreationIsRejected() {
        assertThatThrownBy(() -> service.updateEncounter(Long.MAX_VALUE, change("missing"), login(owner))).isInstanceOf(EncounterNotFoundException.class);
        assertThatThrownBy(() -> service.createEncounter(new EncounterCreateRequest(visitId, "x", "x", "x"), login(owner))).isInstanceOf(InvalidVisitStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM encounters WHERE visit_id=?", Long.class, visitId)).isEqualTo(1);
    }

    @Test
    void historyIsImmutableAndCannotBeRemovedThroughEntityManager() {
        service.updateEncounter(encounterId, change("changed"), login(owner));
        long history = service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent().getFirst().id();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var snapshot = em.find(EncounterHistory.class, history);
            org.springframework.test.util.ReflectionTestUtils.setField(snapshot, "clinicalNote", "tamper");
        });
        assertThat(service.getEncounterHistory(encounterId, history).clinicalNote()).isEqualTo("original note");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                em.remove(em.find(EncounterHistory.class, history)))).isInstanceOf(RuntimeException.class);
        assertThat(historyCount()).isEqualTo(1);
    }

    long waitingVisit() {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Visit visit = new Visit(patient, null, department, doctor, VisitType.WALK_IN);
            em.persist(visit); em.flush(); return visit.getId();
        });
    }

    String bearer(User user) { return "Bearer " + jwt.createToken(user.getUsername()).getAccessToken().getData(); }
    String state(long id) { return jdbc.queryForObject("SELECT status FROM visits WHERE id=?", String.class, id); }
    Long encounterCount(long visit) { return jdbc.queryForObject("SELECT COUNT(*) FROM encounters WHERE visit_id=?", Long.class, visit); }
    java.time.LocalDateTime completedAt(long id) { return jdbc.queryForObject("SELECT completed_at FROM visits WHERE id=?", java.time.LocalDateTime.class, id); }
    long visitAudit(long id, String action, String result) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_table='visits' AND target_id=? AND action=? AND result=?", Long.class, id, action, result);
    }

    @Test
    void staffCanCancelWaitingVisitWithoutDeletingIt() throws Exception {
        long id = waitingVisit();
        secureMvc.perform(patch("/api/visits/{id}/cancel", id).header("Authorization", bearer(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("접수 취소 성공"));
        assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(completedAt(id)).isNull();
        assertThat(encounterCount(id)).isZero();
        assertThat(visitAudit(id, "CANCEL_VISIT", "SUCCESS")).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "NURSE", "ADMIN"})
    void nonStaffCannotCancelVisitViaApi(Role role) throws Exception {
        long id = waitingVisit();
        User actor = role == Role.DOCTOR ? owner : role == Role.NURSE ? nurse : admin;
        secureMvc.perform(patch("/api/visits/{id}/cancel", id).header("Authorization", bearer(actor)))
                .andExpect(status().isForbidden());
        assertThat(state(id)).isEqualTo("WAITING");
        assertThat(visitAudit(id, "CANCEL_VISIT", "SUCCESS")).isZero();
        assertThat(visitAudit(id, "CANCEL_VISIT", "DENIED")).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = VisitStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void onlyWaitingVisitsCanBeCancelled(VisitStatus initial) throws Exception {
        long id = waitingVisit();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Visit visit = em.find(Visit.class, id);
            if (initial == VisitStatus.CANCELLED) visit.cancel();
            else { visit.start(); if (initial == VisitStatus.COMPLETED) visit.complete(); }
        });
        var completionTime = completedAt(id);
        secureMvc.perform(patch("/api/visits/{id}/cancel", id).header("Authorization", bearer(staff)))
                .andExpect(status().isConflict());
        assertThat(state(id)).isEqualTo(initial.name());
        assertThat(completedAt(id)).isEqualTo(completionTime);
        assertThat(visitAudit(id, "CANCEL_VISIT", "SUCCESS")).isZero();
    }

    @Test
    void cancellingBookedVisitDoesNotChangeItsAppointment() throws Exception {
        long[] ids = new TransactionTemplate(transactionManager).execute(tx -> {
            var appointment = new Appointment(doctor, patient, LocalDate.now().plusDays(1).atTime(9, 0), "fixture");
            em.persist(appointment);
            var visit = new Visit(patient, appointment, department, doctor, VisitType.APPOINTMENT);
            em.persist(visit); em.flush(); return new long[]{appointment.getId(), visit.getId()};
        });
        String before = jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, ids[0]);
        secureMvc.perform(patch("/api/visits/{id}/cancel", ids[1]).header("Authorization", bearer(staff)))
                .andExpect(status().isOk());
        assertThat(state(ids[1])).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, ids[0])).isEqualTo(before);
    }

    @Test
    void assignedDoctorCanStartThenCreateEncounterWithAutomaticCompletion() throws Exception {
        long id = waitingVisit();
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("진료 시작 성공"));
        assertThat(state(id)).isEqualTo("IN_PROGRESS");
        assertThat(completedAt(id)).isNull();
        assertThat(visitAudit(id, "START_VISIT", "SUCCESS")).isEqualTo(1);
        authenticate(owner);
        Long record = service.createEncounter(new EncounterCreateRequest(id, "complaint", "note", "plan"), login(owner));
        assertThat(encounterCount(id)).isEqualTo(1);
        assertThat(state(id)).isEqualTo("COMPLETED");
        assertThat(completedAt(id)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CREATE_ENCOUNTER' AND target_id=? AND result='SUCCESS'", Long.class, record)).isEqualTo(1);
        assertThat(visitAudit(id, "COMPLETE_VISIT", "SUCCESS")).isZero();
    }

    @Test
    void anotherDoctorCannotStartVisitViaApi() throws Exception {
        long id = waitingVisit();
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(otherDoctor)))
                .andExpect(status().isForbidden());
        assertThat(state(id)).isEqualTo("WAITING");
        assertThat(visitAudit(id, "START_VISIT", "SUCCESS")).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STAFF", "NURSE", "ADMIN"})
    void nonDoctorsCannotStartVisitViaApi(Role role) throws Exception {
        long id = waitingVisit();
        User actor = role == Role.STAFF ? staff : role == Role.NURSE ? nurse : admin;
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(actor)))
                .andExpect(status().isForbidden());
        assertThat(state(id)).isEqualTo("WAITING");
    }

    @Test
    void duplicateStartIsRejected() throws Exception {
        long id = waitingVisit();
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(owner))).andExpect(status().isOk());
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(owner))).andExpect(status().isConflict());
        assertThat(state(id)).isEqualTo("IN_PROGRESS");
        assertThat(visitAudit(id, "START_VISIT", "SUCCESS")).isEqualTo(1);
        assertThat(visitAudit(id, "START_VISIT", "FAILURE")).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = VisitStatus.class, names = {"COMPLETED", "CANCELLED"})
    void terminalVisitsCannotBeStarted(VisitStatus initial) throws Exception {
        long id = waitingVisit();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Visit visit = em.find(Visit.class, id);
            if (initial == VisitStatus.CANCELLED) visit.cancel(); else { visit.start(); visit.complete(); }
        });
        secureMvc.perform(patch("/api/visits/{id}/start", id).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        assertThat(state(id)).isEqualTo(initial.name());
    }

    @Test
    void waitingVisitCannotHaveEncounterCreated() {
        long id = waitingVisit();
        assertThatThrownBy(() -> service.createEncounter(new EncounterCreateRequest(id, "x", "x", "x"), login(owner)))
                .isInstanceOf(InvalidVisitStatusException.class);
        assertThat(state(id)).isEqualTo("WAITING");
        assertThat(encounterCount(id)).isZero();
        assertThat(completedAt(id)).isNull();
    }

    @Test
    void encounterInsertFailureLeavesVisitInProgress() {
        long id = waitingVisit();
        visitService.startVisit(id, login(owner));
        jdbc.execute("""
                CREATE TRIGGER test_encounter_insert_failure BEFORE INSERT ON encounters FOR EACH ROW
                BEGIN
                  IF NEW.visit_id = %d THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test-only insert failure';
                  END IF;
                END
                """.formatted(id));
        try {
            assertThatThrownBy(() -> service.createEncounter(new EncounterCreateRequest(id, "x", "x", "x"), login(owner)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(state(id)).isEqualTo("IN_PROGRESS");
            assertThat(completedAt(id)).isNull();
            assertThat(encounterCount(id)).isZero();
            assertThat(visitAudit(id, "COMPLETE_VISIT", "SUCCESS")).isZero();
        } finally { jdbc.execute("DROP TRIGGER test_encounter_insert_failure"); }
    }

    @Test
    void failedCompletionRollsBackAlreadyInsertedEncounter() {
        long id = waitingVisit();
        visitService.startVisit(id, login(owner));
        jdbc.execute("""
                CREATE TRIGGER test_visit_completion_failure BEFORE UPDATE ON visits FOR EACH ROW
                BEGIN
                  IF NEW.id = %d AND NEW.status = 'COMPLETED' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test-only completion failure';
                  END IF;
                END
                """.formatted(id));
        long successfulCreates = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND action='CREATE_ENCOUNTER' AND result='SUCCESS'", Long.class, owner.getId());
        try {
            assertThatThrownBy(() -> service.createEncounter(new EncounterCreateRequest(id, "x", "x", "x"), login(owner)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(state(id)).isEqualTo("IN_PROGRESS");
            assertThat(completedAt(id)).isNull();
            assertThat(encounterCount(id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND action='CREATE_ENCOUNTER' AND result='SUCCESS'", Long.class, owner.getId())).isEqualTo(successfulCreates);
        } finally { jdbc.execute("DROP TRIGGER test_visit_completion_failure"); }
    }

    @Test
    void encounterEditPreservesCompletionTimeAndPreviousVersion() {
        var completionTime = completedAt(visitId);
        assertThat(completionTime).isNotNull();
        service.updateEncounter(encounterId, change("after completion"), login(owner));
        assertThat(state(visitId)).isEqualTo("COMPLETED");
        assertThat(completedAt(visitId)).isEqualTo(completionTime);
        assertThat(version()).isEqualTo(2);
        assertThat(historyCount()).isEqualTo(1);
        assertThat(service.getEncounterHistories(encounterId, PageRequest.of(0, 20)).getContent().getFirst().clinicalNote()).isEqualTo("original note");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void outerRollbackLeavesNoSuccessfulVisitStateAudit(boolean start) {
        long id = waitingVisit();
        authenticate(start ? owner : staff);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            if (start) visitService.startVisit(id, login(owner)); else visitService.cancelVisit(id);
            assertThat(visitAudit(id, start ? "START_VISIT" : "CANCEL_VISIT", "SUCCESS")).isZero();
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state(id)).isEqualTo("WAITING");
        assertThat(visitAudit(id, start ? "START_VISIT" : "CANCEL_VISIT", "SUCCESS")).isZero();
    }

    @Test
    void outerRollbackRestoresVisitAndRemovesNewEncounterWithoutSuccessLog() {
        long id = waitingVisit();
        visitService.startVisit(id, login(owner));
        Long[] rolledBackId = {null};
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            rolledBackId[0] = service.createEncounter(new EncounterCreateRequest(id, "x", "x", "x"), login(owner));
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state(id)).isEqualTo("IN_PROGRESS");
        assertThat(completedAt(id)).isNull();
        assertThat(encounterCount(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CREATE_ENCOUNTER' AND target_id=? AND result='SUCCESS'", Long.class, rolledBackId[0])).isZero();
    }

    List<Throwable> twoRequests(User firstUser, Runnable first, User secondUser, Runnable second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        try {
            List<Runnable> actions = List.of(first, second);
            List<User> actors = List.of(firstUser, secondUser);
            for (int i = 0; i < 2; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    authenticate(actors.get(index)); ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        return catchThrowable(actions.get(index)::run);
                    } finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @RepeatedTest(5)
    void cancellationAndStartRaceHasExactlyOneSuccessfulTransition() throws Exception {
        long id = waitingVisit();
        var results = twoRequests(staff, () -> visitService.cancelVisit(id), owner, () -> visitService.startVisit(id, login(owner)));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(results.stream().filter(e -> e instanceof InvalidVisitStatusException).count()).isEqualTo(1);
        assertThat(state(id)).isEqualTo(results.getFirst() == null ? "CANCELLED" : "IN_PROGRESS");
        assertThat(completedAt(id)).isNull();
        assertThat(visitAudit(id, "START_VISIT", "SUCCESS") + visitAudit(id, "CANCEL_VISIT", "SUCCESS")).isEqualTo(1);
        assertThat(visitAudit(id, "START_VISIT", "FAILURE") + visitAudit(id, "CANCEL_VISIT", "FAILURE")).isEqualTo(1);
        System.out.println("VISIT_STATE_RACE requests=2 success=1 stateConflict=1 state=" + state(id));
    }

    @RepeatedTest(3)
    void concurrentEncounterCreationHasOnlyOneRecordAndOneCompletion() throws Exception {
        long id = waitingVisit();
        visitService.startVisit(id, login(owner));
        var request = new EncounterCreateRequest(id, "x", "x", "x");
        var results = twoRequests(owner, () -> service.createEncounter(request, login(owner)), owner, () -> service.createEncounter(request, login(owner)));
        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(results.stream().filter(e -> e instanceof InvalidVisitStatusException).count()).isEqualTo(1);
        assertThat(encounterCount(id)).isEqualTo(1);
        assertThat(state(id)).isEqualTo("COMPLETED");
        assertThat(completedAt(id)).isNotNull();
        Long record = jdbc.queryForObject("SELECT id FROM encounters WHERE visit_id=?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CREATE_ENCOUNTER' AND target_id=? AND result='SUCCESS'", Long.class, record)).isEqualTo(1);
        assertThat(visitAudit(id, "COMPLETE_VISIT", "SUCCESS")).isZero();
        System.out.println("VISIT_ENCOUNTER_RACE requests=2 success=1 stateConflict=1 records=1 state=COMPLETED");
    }

    @Test
    void missingVisitsAndAnonymousRequestsAreRejectedAndNoCompleteApiExists() throws Exception {
        secureMvc.perform(patch("/api/visits/{id}/cancel", Long.MAX_VALUE).header("Authorization", bearer(staff))).andExpect(status().isNotFound());
        secureMvc.perform(patch("/api/visits/{id}/start", Long.MAX_VALUE).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        secureMvc.perform(patch("/api/visits/{id}/cancel", visitId).with(anonymous())).andExpect(status().isUnauthorized());
        secureMvc.perform(patch("/api/visits/{id}/start", visitId).with(anonymous())).andExpect(status().isUnauthorized());
        secureMvc.perform(patch("/api/visits/{id}/complete", visitId).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
    }

    @Test
    void activeVisitQueryUsesCheckInTimeWithInclusiveStartAndExclusiveEnd() {
        long id = waitingVisit();
        var from = LocalDate.now().atStartOfDay();
        var until = from.plusDays(1);
        var statuses = List.of(VisitStatus.WAITING, VisitStatus.IN_PROGRESS);

        setCheckInTime(id, from.minusSeconds(1));
        assertThat(visitRepository.existsActiveVisit(patient.getId(), department.getId(), from, until, statuses)).isFalse();
        setCheckInTime(id, from);
        assertThat(visitRepository.existsActiveVisit(patient.getId(), department.getId(), from, until, statuses)).isTrue();
        setCheckInTime(id, until.minusSeconds(1));
        assertThat(visitRepository.existsActiveVisit(patient.getId(), department.getId(), from, until, statuses)).isTrue();
        setCheckInTime(id, until);
        assertThat(visitRepository.existsActiveVisit(patient.getId(), department.getId(), from, until, statuses)).isFalse();
    }

    @Test
    void activeVisitQueryRequiresSamePatientDepartmentAndActiveStatus() {
        long id = waitingVisit();
        var from = LocalDate.now().atStartOfDay();
        var until = from.plusDays(1);
        var statuses = List.of(VisitStatus.WAITING, VisitStatus.IN_PROGRESS);
        setCheckInTime(id, from.plusHours(9));
        for (VisitStatus status : VisitStatus.values()) {
            jdbc.update("UPDATE visits SET status=? WHERE id=?", status.name(), id);
            assertThat(visitRepository.existsActiveVisit(patient.getId(), department.getId(), from, until, statuses))
                    .as("status=%s", status).isEqualTo(status == VisitStatus.WAITING || status == VisitStatus.IN_PROGRESS);
        }
        jdbc.update("UPDATE visits SET status='WAITING' WHERE id=?", id);
        assertThat(visitRepository.existsActiveVisit(Long.MAX_VALUE, department.getId(), from, until, statuses)).isFalse();
        assertThat(visitRepository.existsActiveVisit(patient.getId(), Long.MAX_VALUE, from, until, statuses)).isFalse();
    }

    void setCheckInTime(long id, java.time.LocalDateTime time) {
        // Use the same typed Hibernate binding as production, rather than mixing raw JDBC timestamp handling.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> em.createQuery(
                "update Visit v set v.checkedInAt = :time where v.id = :id")
                .setParameter("time", time).setParameter("id", id).executeUpdate());
        java.time.LocalDateTime persisted = new TransactionTemplate(transactionManager)
                .execute(tx -> em.find(Visit.class, id).getCheckedInAt());
        assertThat(persisted).isEqualTo(time);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.example.emr")
    @EnableJpaRepositories(basePackageClasses = {EncounterRepository.class, EncounterHistoryRepository.class, UserRepository.class, AuditLogRepository.class, VisitRepository.class})
    @EnableTransactionManagement
    @EnableMethodSecurity
    @EnableAspectJAutoProxy
    @Import({EncounterService.class, AuditAspect.class, AuditLogService.class,
            SecurityConfig.class, JwtProvider.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EncounterController.class, VisitController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean VisitService visitService(VisitRepository visits) {
            return new VisitService(visits, mock(AppointmentService.class), mock(PatientRepository.class),
                    mock(com.example.emr.doctors.repository.DoctorRepository.class),
                    mock(com.example.emr.appointments.repository.AppointmentRepository.class),
                    mock(EncounterRepository.class));
        }
    }
}
