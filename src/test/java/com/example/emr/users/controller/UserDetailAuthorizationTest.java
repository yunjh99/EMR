package com.example.emr.users.controller;

import com.example.emr.global.security.JwtProvider;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "jwt.secret-key=01234567890123456789012345678901")
// H2 대신 실제 애플리케이션의 MySQL 연결 사용. 기존 테이블은 검증만 수행
@TestPropertySource(locations = "file:src/main/resources/application.properties",
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional
class UserDetailAuthorizationTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository repository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mvc;
    private User target;
    private final List<Long> fixtureIds = new ArrayList<>();
    private final String usernamePrefix = "it" + UUID.randomUUID().toString().substring(0, 8) + "_";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        target = createFixture("target", "대상", Role.STAFF);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "NURSE", "STAFF"})
    void employeesCanReadOnlyTheirOwnDetails(Role role) throws Exception {
        var actor = createFixture("actor", "조회자", role);
        var bearer = bearer(actor);

        mvc.perform(get("/api/users/{userId}", actor.getId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(actor.getId()))
                .andExpect(jsonPath("$.data.role").value(role.name()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mvc.perform(get("/api/users/{userId}", target.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnotherUserAndGets404ForMissingUser() throws Exception {
        var admin = createFixture("admin", "관리자", Role.ADMIN);

        mvc.perform(get("/api/users/{userId}", target.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(target.getId()));

        mvc.perform(get("/api/users/{userId}", Long.MAX_VALUE).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/users/{userId}", target.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanChangeRoleAndItIsPersisted() throws Exception {
        var admin = createFixture("admin", "관리자", Role.ADMIN);

        mvc.perform(patch("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"DOCTOR\"}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(repository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCanDeactivateAndReactivateAndExistingTokenIsChecked() throws Exception {
        var admin = createFixture("admin", "관리자", Role.ADMIN);
        String targetToken = bearer(target);

        mvc.perform(patch("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(target.getId()).orElseThrow().isActive()).isFalse();
        assertThat(repository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.STAFF);
        mvc.perform(get("/api/users/{id}", target.getId()).header("Authorization", targetToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(target.getId()).orElseThrow().isActive()).isTrue();
        mvc.perform(get("/api/users/{id}", target.getId()).header("Authorization", targetToken))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"DOCTOR", "NURSE", "STAFF"})
    void nonAdminsCannotChangeRoleOrActiveState(Role role) throws Exception {
        var actor = createFixture("actor", "조회자", role);
        mvc.perform(patch("/api/users/{id}", actor.getId())
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsExplicitNullValuesAndUnknownUsers() throws Exception {
        var admin = createFixture("admin", "관리자", Role.ADMIN);
        for (String body : new String[]{"{\"role\":null}", "{\"active\":null}",
                "{\"role\":\"INVALID\"}", "{\"role\":\"DOCTOR\",\"active\":null}"}) {
            mvc.perform(patch("/api/users/{id}", target.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/users/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"NURSE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesBothFieldsInOneRequest() throws Exception {
        var admin = createFixture("admin", "관리자", Role.ADMIN);
        mvc.perform(patch("/api/users/{id}", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"NURSE\",\"active\":false}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        User updated = repository.findById(target.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.NURSE);
        assertThat(updated.isActive()).isFalse();
    }

    private User createFixture(String label, String name, Role role) {
        // 감사 로그의 별도 트랜잭션에서도 작업자 계정을 참조할 수 있도록 먼저 커밋
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        User user = transaction.execute(status -> repository.saveAndFlush(
                new User(usernamePrefix + label, "hash", name, role)));
        fixtureIds.add(user.getId());
        return user;
    }

    @AfterTransaction
    void cleanUpFixtures() {
        // 업무 변경은 테스트 트랜잭션이 롤백. 별도로 커밋된 테스트 로그와 계정만 정리
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long userId : fixtureIds) {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
            }
            for (Long userId : fixtureIds) {
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            }
        });
        fixtureIds.clear();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createToken(user.getUsername()).getAccessToken().getData();
    }
}
