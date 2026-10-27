package com.example.emr.doctors.controller;

import com.example.emr.departments.domain.Department;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.doctors.repository.DoctorRepository;
import com.example.emr.global.security.JwtProvider;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:management-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
class ManagementIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    Department department;
    User doctorUser;
    Doctor doctor;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        department = departments.saveAndFlush(new Department("IM", "내과"));
        doctorUser = users.saveAndFlush(new User("existing_doctor", "hash", "김의사", Role.DOCTOR));
        doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "LIC-1", "소화기"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM doctors");
        jdbc.update("DELETE FROM departments");
        jdbc.update("DELETE FROM audit_logs");
        users.deleteAll();
    }

    @Test
    void departmentLifecycleUsesOneAuditPerChangeAndSoftDelete() throws Exception {
        String token = bearer(Role.ADMIN);
        mvc.perform(post("/api/departments").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OS\",\"name\":\"정형외과\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data").doesNotExist());
        Long id = jdbc.queryForObject("SELECT id FROM departments WHERE code = 'OS'", Long.class);
        mvc.perform(put("/api/departments/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ORT\",\"name\":\"정형외과 수정\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/departments/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("ORT"));
        mvc.perform(patch("/api/departments/{id}/deactivate", id).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/departments/{id}", id).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/departments/{id}?includeInactive=true", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        assertThat(departments.findById(id)).isPresent();
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE result = 'SUCCESS'", String.class))
                .containsExactlyInAnyOrder("CREATE_DEPARTMENT", "UPDATE_DEPARTMENT", "DEACTIVATE_DEPARTMENT");
    }

    @Test
    void duplicateDepartmentCodeAndUpdateConflictReturn409() throws Exception {
        String token = bearer(Role.ADMIN);
        mvc.perform(post("/api/departments").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IM\",\"name\":\"중복\"}"))
                .andExpect(status().isConflict());
        var other = departments.saveAndFlush(new Department("OS", "정형외과"));
        mvc.perform(put("/api/departments/{id}", other.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"IM\",\"name\":\"중복\"}"))
                .andExpect(status().isConflict());
        assertThat(departments.findById(other.getId()).orElseThrow().getCode()).isEqualTo("OS");
        assertThat(jdbc.queryForList("SELECT failure_reason FROM audit_logs", String.class))
                .containsOnly("DUPLICATE_DEPARTMENT_CODE");
    }

    @Test
    void existingAccountLinkingAndDoctorUpdatePreserveReferences() throws Exception {
        String token = bearer(Role.ADMIN);
        var user = users.saveAndFlush(new User("second_doctor", "hash", "이의사", Role.DOCTOR));
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(existingRequest(user.getId(), department.getId(), "LIC-2")))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", Long.class, user.getId());
        var other = departments.saveAndFlush(new Department("OS", "정형외과"));
        mvc.perform(put("/api/doctors/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":%d,\"licenseNo\":\"LIC-3\",\"specialty\":null}".formatted(other.getId())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/doctors/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.departmentId").value(other.getId()))
                .andExpect(jsonPath("$.data.licenseNo").value("LIC-3"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(patch("/api/doctors/{id}/deactivate", id).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/doctors/{id}", id).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/doctors/{id}?includeInactive=true", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id = ?", Long.class, id)).isEqualTo(user.getId());
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isTrue();
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE result = 'SUCCESS'", String.class))
                .containsExactlyInAnyOrder("CREATE_DOCTOR", "UPDATE_DOCTOR", "DEACTIVATE_DOCTOR");
    }

    @Test
    void newAccountIsHashedAndCreatedAtomicallyWithDoctor() throws Exception {
        String token = bearer(Role.ADMIN);
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("new_doctor", department.getId(), "LIC-NEW")))
                .andExpect(status().isCreated());
        var user = users.findByUsername("new_doctor").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(encoder.matches("password123!", user.getPasswordHash())).isTrue();
        assertThat(doctors.existsByUserId(user.getId())).isTrue();
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs", String.class))
                .containsExactlyInAnyOrder("CREATE", "CREATE_DOCTOR");
    }

    @Test
    void doctorInsertFailureRollsBackNewAccountAndSuccessAuditLogs() throws Exception {
        String token = bearer(Role.ADMIN);
        jdbc.execute("ALTER TABLE doctors ADD CONSTRAINT reject_license CHECK (license_no <> 'REJECT')");
        try {
            mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content(accountRequest("rollback_doctor", department.getId(), "REJECT")))
                    .andExpect(status().isConflict());
            assertThat(users.existsByUsername("rollback_doctor")).isFalse();
            assertThat(doctors.count()).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT result FROM audit_logs", String.class)).containsExactly("FAILURE");
        } finally {
            jdbc.execute("ALTER TABLE doctors DROP CONSTRAINT reject_license");
        }
    }

    @Test
    void duplicateUserAndLicenseReturnConflictEvenForInactiveDoctors() throws Exception {
        String token = bearer(Role.ADMIN);
        doctor.deactivate();
        doctors.saveAndFlush(doctor);
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(existingRequest(doctorUser.getId(), department.getId(), "LIC-OTHER")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("unused_doctor", department.getId(), "LIC-1")))
                .andExpect(status().isConflict());
        assertThat(users.existsByUsername("unused_doctor")).isFalse();
    }

    @Test
    void nonexistentOrInactiveDepartmentCannotReceiveDoctors() throws Exception {
        String token = bearer(Role.ADMIN);
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("missing_dep", Long.MAX_VALUE, "LIC-X")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/departments/{id}/deactivate", department.getId()).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("inactive_dep", department.getId(), "LIC-X")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/doctors/{id}", doctor.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":%d,\"licenseNo\":\"LIC-1\"}".formatted(department.getId())))
                .andExpect(status().isConflict());
        assertThat(users.existsByUsername("missing_dep")).isFalse();
        assertThat(users.existsByUsername("inactive_dep")).isFalse();
        assertThat(doctors.findById(doctor.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT department_id FROM doctors WHERE id = ?", Long.class, doctor.getId()))
                .isEqualTo(department.getId());
        mvc.perform(get("/api/doctors").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void inactiveOrNonDoctorAccountCannotBeLinked() throws Exception {
        String token = bearer(Role.ADMIN);
        var inactive = new User("inactive", "hash", "비활성", Role.DOCTOR);
        inactive.update(null, null, false);
        users.saveAndFlush(inactive);
        var staff = users.saveAndFlush(new User("staff_target", "hash", "직원", Role.STAFF));
        for (Long id : new Long[]{inactive.getId(), staff.getId()}) {
            mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content(existingRequest(id, department.getId(), "LIC-X")))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(existingRequest(Long.MAX_VALUE, department.getId(), "LIC-X")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void allEmployeeRolesCanReadActiveListsAndDetails(Role role) throws Exception {
        String token = bearer(role);
        mvc.perform(get("/api/departments").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/departments/{id}", department.getId()).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/doctors?departmentId={id}", department.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/doctors/{id}", doctor.getId()).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/doctors?departmentId={id}", Long.MAX_VALUE).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "NURSE", "STAFF"})
    void nonAdminsCannotWriteOrReadInactiveEntries(Role role) throws Exception {
        String token = bearer(role);
        mvc.perform(post("/api/departments").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OS\",\"name\":\"정형외과\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/departments/{id}", department.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"IM\",\"name\":\"변경\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/departments/{id}/deactivate", department.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/doctors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("forbidden", department.getId(), "LIC-F")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/doctors/{id}", doctor.getId()).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":%d,\"licenseNo\":\"LIC-1\"}".formatted(department.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/doctors/{id}/deactivate", doctor.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/api/departments", "/api/doctors", "/api/departments/" + department.getId(), "/api/doctors/" + doctor.getId()}) {
            mvc.perform(get(path).param("includeInactive", "true").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        assertThat(users.existsByUsername("forbidden")).isFalse();
        assertThat(jdbc.queryForList("SELECT result FROM audit_logs", String.class)).hasSize(6).containsOnly("DENIED");
    }

    @Test
    void unauthenticatedAndInactiveAccountsAreRejected() throws Exception {
        for (String path : new String[]{"/api/departments", "/api/doctors"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        String token = bearer(Role.ADMIN);
        var actor = users.findByUsername("actor_ADMIN").orElseThrow();
        actor.update(null, null, false);
        users.saveAndFlush(actor);
        mvc.perform(get("/api/doctors").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveAccountOrChangedRoleExcludesDoctorFromActiveRead() throws Exception {
        String token = bearer(Role.STAFF);
        doctorUser.update(null, null, false);
        users.saveAndFlush(doctorUser);
        mvc.perform(get("/api/doctors").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        doctorUser.update(null, Role.NURSE, true);
        users.saveAndFlush(doctorUser);
        mvc.perform(get("/api/doctors/{id}", doctor.getId()).header("Authorization", token)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":\" \",\"name\":\"내과\"}", "{\"code\":\"IM\",\"name\":null}",
            "{\"code\":\"HAS SPACE\",\"name\":\"내과\"}"})
    void invalidDepartmentsAreRejected(String body) throws Exception {
        mvc.perform(post("/api/departments").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"departmentId\":1,\"licenseNo\":\"LIC-X\"}",
            "{\"userId\":1,\"account\":{\"username\":\"doctor\",\"password\":\"pass\",\"name\":\"의사\"},\"departmentId\":1,\"licenseNo\":\"LIC-X\"}",
            "{\"userId\":-1,\"departmentId\":1,\"licenseNo\":\"LIC-X\"}",
            "{\"userId\":1,\"departmentId\":1,\"licenseNo\":\" \"}",
            "{\"account\":{\"username\":\"doctor\",\"password\":\"\",\"name\":\"의사\"},\"departmentId\":1,\"licenseNo\":\"LIC-X\"}"
    })
    void invalidDoctorRequestsAreRejectedBeforeAccountCreation(String body) throws Exception {
        mvc.perform(post("/api/doctors").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(doctors.count()).isEqualTo(1);
    }

    @Test
    void missingDetailsAndMutationTargetsReturn404() throws Exception {
        String token = bearer(Role.ADMIN);
        for (String path : new String[]{"/api/departments/", "/api/doctors/"}) {
            mvc.perform(get(path + Long.MAX_VALUE).header("Authorization", token)).andExpect(status().isNotFound());
            mvc.perform(patch(path + Long.MAX_VALUE + "/deactivate").header("Authorization", token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void databaseEnforcesUniqueKeysAndForeignKeys() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO departments (code, name, is_active, created_at) VALUES ('IM', '중복', TRUE, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO doctors (user_id, department_id, license_no, is_active, created_at)
                VALUES (?, ?, 'OTHER', TRUE, CURRENT_TIMESTAMP)
                """, doctorUser.getId(), department.getId())).isInstanceOf(DataIntegrityViolationException.class);
        var other = users.saveAndFlush(new User("constraint_doctor", "hash", "의사", Role.DOCTOR));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO doctors (user_id, department_id, license_no, is_active, created_at)
                VALUES (?, ?, 'LIC-1', TRUE, CURRENT_TIMESTAMP)
                """, other.getId(), department.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM departments WHERE id = ?", department.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", doctorUser.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO doctors (user_id, department_id, license_no, is_active, created_at)
                VALUES (?, ?, 'NO-DEP', TRUE, CURRENT_TIMESTAMP)
                """, other.getId(), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private String bearer(Role role) {
        String username = "actor_" + role.name();
        users.saveAndFlush(new User(username, "hash", "직원", role));
        return "Bearer " + jwt.createToken(username).getAccessToken().getData();
    }

    private String existingRequest(Long userId, Long departmentId, String license) {
        return "{\"userId\":%d,\"departmentId\":%d,\"licenseNo\":\"%s\"}".formatted(userId, departmentId, license);
    }

    private String accountRequest(String username, Long departmentId, String license) {
        return """
                {"account":{"username":"%s","password":"password123!","name":"새 의사"},
                 "departmentId":%d,"licenseNo":"%s","specialty":"내과"}
                """.formatted(username, departmentId, license);
    }
}
