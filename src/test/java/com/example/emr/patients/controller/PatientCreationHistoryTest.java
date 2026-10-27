package com.example.emr.patients.controller;

import com.example.emr.global.security.JwtProvider;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:patient-creation-history;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PatientCreationHistoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String bearer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        users.saveAndFlush(new User("creation_staff", "hash", "직원", Role.STAFF));
        bearer = "Bearer " + jwtProvider.createToken("creation_staff").getAccessToken().getData();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM patient_histories");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM patients");
        users.deleteAll();
    }

    @Test
    void creationLinksInitialValuesToOneAuditLog() throws Exception {
        createPatient("010-1111-2222", 201);
        var logs = jdbc.queryForList("SELECT * FROM audit_logs");
        assertThat(logs).hasSize(1);
        var log = logs.getFirst();
        assertThat(log.get("action")).isEqualTo("CREATE_PATIENT");
        assertThat(log.get("result")).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM patient_histories
                WHERE audit_log_id = ? AND patient_id = ? AND old_value IS NULL
                """, Integer.class, log.get("id"), log.get("target_id"))).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT new_value FROM patient_histories WHERE audit_log_id = ? AND field_name = 'phone'
                """, String.class, log.get("id"))).isEqualTo("010-1111-2222");
    }

    @Test
    void initialHistoryFailureRollsBackCreationAndSuccessLog() throws Exception {
        jdbc.execute("""
                ALTER TABLE patient_histories ADD CONSTRAINT reject_creation_history
                CHECK (new_value <> 'reject-history')
                """);
        try {
            createPatient("reject-history", 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_histories", Integer.class)).isZero();
            var logs = jdbc.queryForList("SELECT * FROM audit_logs");
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().get("result")).isEqualTo("FAILURE");
        } finally {
            jdbc.execute("ALTER TABLE patient_histories DROP CONSTRAINT reject_creation_history");
        }
    }

    private void createPatient(String phone, int expectedStatus) throws Exception {
        mvc.perform(post("/api/patients")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수", "phone":"%s", "address":"서울",
                                 "gender":"M", "birthDate":"1990-01-02"}
                                """.formatted(phone)))
                .andExpect(status().is(expectedStatus));
    }
}
