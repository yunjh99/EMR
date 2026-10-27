package com.example.emr.patients.controller;

import com.example.emr.global.security.JwtProvider;
import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.repository.PatientRepository;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:patient-update-test;DB_CLOSE_DELAY=-1")
class PatientUpdateTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mvc;
    private Patient patient;
    private User actor;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        patient = patients.saveAndFlush(new Patient("P0001", "기존 이름", "010-0000-0000",
                "기존 주소", Gender.F, LocalDate.of(1990, 1, 2)));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM patient_histories WHERE patient_id = ?", patient.getId());
        if (actor != null) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE user_id = ?", actor.getId());
            users.deleteById(actor.getId());
        }
        patients.deleteById(patient.getId());
    }

    @Test
    void staffCanPatchOneFieldAndAuditIsCommitted() throws Exception {
        String bearer = bearer(Role.STAFF);
        mvc.perform(patch("/api/patients/{id}", patient.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"010-1111-2222\"}"))
                .andExpect(status().isOk());

        Patient updated = patients.findById(patient.getId()).orElseThrow();
        assertThat(updated.getPhone()).isEqualTo("010-1111-2222");
        assertThat(updated.getName()).isEqualTo(patient.getName());
        assertThat(updated.getPatientNo()).isEqualTo(patient.getPatientNo());
        assertThat(updated.getBirthDate()).isEqualTo(patient.getBirthDate());
        assertThat(updated.getUpdatedAt()).isNotNull();
        var logs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE user_id = ? AND action = 'UPDATE_PATIENT'", actor.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("action")).isEqualTo("UPDATE_PATIENT");
        assertThat(logs.getFirst().get("target_table")).isEqualTo("patients");
        assertThat(((Number) logs.getFirst().get("target_id")).longValue()).isEqualTo(patient.getId());
        assertThat(logs.getFirst().get("result")).isEqualTo("SUCCESS");

        var changes = jdbcTemplate.queryForList("""
                SELECT * FROM patient_histories WHERE audit_log_id = ?
                """, logs.getFirst().get("id"));
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().get("field_name")).isEqualTo("phone");
        assertThat(changes.getFirst().get("old_value").toString()).contains("010-0000-0000");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM patient_histories h JOIN audit_logs a ON h.audit_log_id = a.id
                WHERE h.patient_id = ? AND a.action = 'BASELINE'
                """, Integer.class, patient.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ?",
                Integer.class, actor.getId())).isEqualTo(1);
    }

    @Test
    void repeatedChangesRecordOnlyChangedFieldsAndUnchangedRequestCreatesNothing() throws Exception {
        String bearer = bearer(Role.STAFF);
        for (String phone : new String[]{"010-1111-2222", "010-3333-4444", "010-3333-4444"}) {
            mvc.perform(patch("/api/patients/{id}", patient.getId())
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'BASELINE'",
                Integer.class, actor.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'UPDATE_PATIENT'",
                Integer.class, actor.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_histories WHERE patient_id = ?",
                Integer.class, patient.getId())).isEqualTo(2);
    }

    @Test
    void historyFailureRollsBackPatientAndSuccessLogs() throws Exception {
        String bearer = bearer(Role.STAFF);
        jdbcTemplate.execute("""
                ALTER TABLE patient_histories ADD CONSTRAINT reject_test_value
                CHECK (new_value <> 'reject-history')
                """);
        try {
            mvc.perform(patch("/api/patients/{id}", patient.getId())
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"reject-history\"}"))
                    .andExpect(status().isConflict());
            assertThat(patients.findById(patient.getId()).orElseThrow().getPhone())
                    .isEqualTo(patient.getPhone());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM patient_histories WHERE patient_id = ?",
                    Integer.class, patient.getId())).isZero();
            var logs = jdbcTemplate.queryForList("SELECT * FROM audit_logs WHERE user_id = ?", actor.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().get("result")).isEqualTo("FAILURE");
        } finally {
            jdbcTemplate.execute("ALTER TABLE patient_histories DROP CONSTRAINT reject_test_value");
        }
    }

    @Test
    void staffCanUpdateAllEditableFields() throws Exception {
        mvc.perform(patch("/api/patients/{id}", patient.getId())
                        .header("Authorization", bearer(Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"새 이름", "birthDate":"2000-03-04", "gender":"M",
                                 "phone":"010-1111-2222", "address":"새 주소"}
                                """))
                .andExpect(status().isOk());

        Patient updated = patients.findById(patient.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("새 이름");
        assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(2000, 3, 4));
        assertThat(updated.getGender()).isEqualTo(Gender.M);
        assertThat(updated.getAddress()).isEqualTo("새 주소");
        assertThat(jdbcTemplate.queryForObject("SELECT gender FROM patients WHERE id = ?",
                String.class, patient.getId())).isEqualTo("M");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "DOCTOR", "NURSE"})
    void otherRolesAreDenied(Role role) throws Exception {
        mvc.perform(patch("/api/patients/{id}", patient.getId())
                        .header("Authorization", bearer(role))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경 이름\"}"))
                .andExpect(status().isForbidden());
        assertThat(patients.findById(patient.getId()).orElseThrow().getName()).isEqualTo(patient.getName());
        assertThat(jdbcTemplate.queryForObject("SELECT result FROM audit_logs WHERE user_id = ?",
                String.class, actor.getId())).isEqualTo("DENIED");
    }

    @Test
    void missingPatientReturns404AndFailureCode() throws Exception {
        mvc.perform(patch("/api/patients/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT failure_reason FROM audit_logs WHERE user_id = ?",
                String.class, actor.getId())).isEqualTo("PATIENT_NOT_FOUND");
    }

    @Test
    void invalidValuesAreRejectedBeforeUpdate() throws Exception {
        String bearer = bearer(Role.STAFF);
        for (String body : new String[]{"{\"name\":null}", "{\"name\":\"   \"}",
                "{\"phone\":\"\"}", "{\"address\":null}", "{\"birthDate\":null}",
                "{\"gender\":\"INVALID\"}", "{\"birthDate\":\"2000-02-30\"}"}) {
            mvc.perform(patch("/api/patients/{id}", patient.getId())
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(patients.findById(patient.getId()).orElseThrow().getName()).isEqualTo(patient.getName());
    }

    @Test
    void missingAuthenticationIsRejected() throws Exception {
        mvc.perform(patch("/api/patients/{id}", patient.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(Role role) {
        actor = users.saveAndFlush(new User("patient_staff_test", "hash", "테스트 직원", role));
        return "Bearer " + jwtProvider.createToken(actor.getUsername()).getAccessToken().getData();
    }
}
