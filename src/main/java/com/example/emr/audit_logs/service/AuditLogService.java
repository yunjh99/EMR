package com.example.emr.audit_logs.service;

import com.example.emr.audit_logs.domain.AuditLog;
import com.example.emr.audit_logs.repository.AuditLogRepository;
import com.example.emr.audit_logs.dto.AuditLogCommand;
import com.example.emr.audit_logs.domain.AuditResult;
import com.example.emr.users.dto.LoginUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // 변경 이력과 연결되는 성공 로그는 업무 트랜잭션에서 함께 저장한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog writeEntry(String action, String targetTable, Long targetId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new AccessDeniedException("Authenticated user required for patient history");
        }
        var actor = (LoginUser) authentication.getPrincipal();
        return auditLogRepository.save(new AuditLog(actor.userId(), action, targetTable,
                targetId, AuditResult.SUCCESS, null));
    }

    // 업무 트랜잭션이 롤백되어도 로그를 보존하도록 별도 트랜잭션 사용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLogCommand command) {
        AuditLog auditLog = new AuditLog(
                command.userId(),
                command.action(),
                command.targetTable(),
                command.targetId(),
                command.result(),
                command.failureReason()
        );
        auditLogRepository.save(auditLog);
    }

    // TODO: 감사 로그 조회 로직
}
