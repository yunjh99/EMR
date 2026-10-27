package com.example.emr.audit_logs.service;

import com.example.emr.audit_logs.domain.AuditResult;
import com.example.emr.audit_logs.dto.AuditLogCommand;
import com.example.emr.audit_logs.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret-key=01234567890123456789012345678901",
        "spring.datasource.url=jdbc:h2:mem:audit-write-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditLogServiceTest {
    @Autowired
    private AuditLogService service;
    @Autowired
    private AuditLogRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void savesCommandAndGeneratesIdAndCreatedAt() {
        service.write(new AuditLogCommand(1L, "UPDATE_USER", "users", 5L,
                AuditResult.SUCCESS, null));

        var logs = repository.findAll();
        assertThat(logs).hasSize(1);
        var log = logs.getFirst();
        assertThat(log.getId()).isPositive();
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getUserId()).isEqualTo(1L);
        assertThat(log.getAction()).isEqualTo("UPDATE_USER");
        assertThat(log.getTargetTable()).isEqualTo("users");
        assertThat(log.getTargetId()).isEqualTo(5L);
        assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(log.getFailureReason()).isNull();
    }

    @Test
    void auditLogSurvivesOuterTransactionRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.write(new AuditLogCommand(1L, "READ_USERS", "users", null,
                    AuditResult.DENIED, "ACCESS_DENIED"));
            status.setRollbackOnly();
        });

        var logs = repository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getResult()).isEqualTo(AuditResult.DENIED);
        assertThat(logs.getFirst().getFailureReason()).isEqualTo("ACCESS_DENIED");
        assertThat(logs.getFirst().getTargetId()).isNull();
    }
}
