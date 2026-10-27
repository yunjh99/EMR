package com.example.emr.audit_logs.aspect;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.audit_logs.domain.AuditResult;
import com.example.emr.audit_logs.repository.AuditLogRepository;
import com.example.emr.global.error.exception.DuplicateUserNameException;
import com.example.emr.global.error.exception.UserNotFoundException;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.dto.UserCreateRequest;
import com.example.emr.users.repository.UserRepository;
import com.example.emr.users.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "jwt.secret-key=01234567890123456789012345678901",
        "spring.datasource.url=jdbc:h2:mem:audit-aspect-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AuditAspectTest.Config.class)
class AuditAspectTest {
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuditLogRepository logs;
    @Autowired
    private ExampleService exampleService;

    private Long actorId;

    @BeforeEach
    void setUp() {
        actorId = users.saveAndFlush(new User("admin", "hash", "관리자", Role.ADMIN)).getId();
        authenticate(Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        logs.deleteAll();
        users.deleteAll();
    }

    @Test
    void creationLogsReturnedIdAfterCommit() {
        Long createdId = userService.createUser(request("newuser"));

        assertThat(users.findById(createdId)).isPresent();
        var log = logs.findAll().getFirst();
        assertThat(log.getUserId()).isEqualTo(actorId);
        assertThat(log.getTargetId()).isEqualTo(createdId);
        assertThat(log.getTargetTable()).isEqualTo("users");
        assertThat(log.getAction()).isEqualTo("CREATE");
        assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(log.getFailureReason()).isNull();
    }

    @Test
    void duplicateFailureIsLoggedWithoutCreatedId() {
        assertThatThrownBy(() -> userService.createUser(request("admin")))
                .isInstanceOf(DuplicateUserNameException.class);

        var log = logs.findAll().getFirst();
        assertThat(log.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(log.getTargetId()).isNull();
        assertThat(log.getFailureReason()).isEqualTo("DUPLICATE_USERNAME");
    }

    @Test
    void methodSecurityDenialIsLogged() {
        authenticate(Role.NURSE);
        assertThatThrownBy(() -> userService.createUser(request("forbidden")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(users.existsByUsername("forbidden")).isFalse();
        var log = logs.findAll().getFirst();
        assertThat(log.getResult()).isEqualTo(AuditResult.DENIED);
        assertThat(log.getFailureReason()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void resolvesTargetIdFromMethodArgument() {
        assertThat(exampleService.read(5L)).isEqualTo("ok");

        var log = logs.findAll().getFirst();
        assertThat(log.getTargetId()).isEqualTo(5L);
        assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void failurePreservesArgumentIdAndOriginalException() {
        assertThatThrownBy(() -> exampleService.read(-1L)).isInstanceOf(UserNotFoundException.class);

        var log = logs.findAll().getFirst();
        assertThat(log.getTargetId()).isEqualTo(-1L);
        assertThat(log.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(log.getFailureReason()).isEqualTo("USER_NOT_FOUND");
    }

    private void authenticate(Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(actorId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private UserCreateRequest request(String username) {
        return new UserCreateRequest(username, "password123!", "직원", Role.STAFF);
    }

    @TestConfiguration
    static class Config {
        @Bean
        ExampleService exampleService() {
            return new ExampleService();
        }
    }

    static class ExampleService {
        @Audit(action = "READ_USER", targetTable = "users", targetId = "#userId")
        @Transactional(readOnly = true)
        public String read(Long userId) {
            if (userId < 0) {
                throw new UserNotFoundException();
            }
            return "ok";
        }
    }
}
