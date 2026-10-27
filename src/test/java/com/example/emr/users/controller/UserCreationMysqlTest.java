package com.example.emr.users.controller;

import com.example.emr.global.security.JwtProvider;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.UserCreateRequest;
import com.example.emr.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 MySQL에 저장하고, DB에서 직접 확인하도록 생성한 계정과 감사 로그를 남기는 테스트
// @Transactional을 붙이지 않아 사용자 생성과 감사 로그가 각각 실제 커밋됨
@SpringBootTest
@TestPropertySource(locations = "file:src/main/resources/application.properties",
        properties = "spring.jpa.hibernate.ddl-auto=validate")
class UserCreationMysqlTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createUserAndAuditLogInMysql() throws Exception {
        // 1. 실제 MySQL 연결인지 확인
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }

        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 2. 요청을 보낼 테스트 관리자 준비. 기존 계정을 변경하지 않음
        User admin = userRepository.saveAndFlush(new User(
                "it_admin_" + suffix,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                "생성 테스트 관리자", Role.ADMIN
        ));
        String accessToken = jwtProvider.createToken(admin.getUsername()).getAccessToken().getData();

        // 3. 관리자 JWT로 실제 컨트롤러 → 서비스 → DB 저장 흐름 실행
        String username = "it_staff_" + suffix;
        String rawPassword = "Test!" + UUID.randomUUID();
        var request = new UserCreateRequest(username, rawPassword, "생성 테스트 직원", Role.STAFF);

        mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("사용자 생성 성공"));

        // 4. 생성한 직원과 비밀번호 해시 확인
        User created = userRepository.findByUsername(username).orElseThrow();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(request.name());
        assertThat(created.getRole()).isEqualTo(Role.STAFF);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, created.getPasswordHash())).isTrue();

        // 5. AOP가 만든 감사 로그가 실제 DB에 커밋됐는지 조회
        var logs = jdbcTemplate.queryForList("""
                SELECT id, user_id, action, target_table, target_id, result, failure_reason, created_at
                FROM audit_logs
                WHERE user_id = ? AND target_table = 'users' AND target_id = ? AND action = 'CREATE'
                """, admin.getId(), created.getId());

        assertThat(logs).hasSize(1);
        var auditLog = logs.getFirst();
        assertThat(auditLog.get("result")).isEqualTo("SUCCESS");
        assertThat(auditLog.get("failure_reason")).isNull();
        assertThat(auditLog.get("created_at")).isNotNull();

        // 비밀번호·JWT는 출력하지 않고 DB 확인에 필요한 ID만 출력
        System.out.printf("MYSQL_CREATE_TEST adminId=%d userId=%d username=%s auditLogId=%s%n",
                admin.getId(), created.getId(), username, auditLog.get("id"));
    }
}
