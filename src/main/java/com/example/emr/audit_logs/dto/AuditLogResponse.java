package com.example.emr.audit_logs.dto;

import com.example.emr.audit_logs.domain.AuditLog;
import com.example.emr.audit_logs.domain.AuditResult;

import java.time.LocalDateTime;

// 저장된 감사 로그를 조회 API의 응답으로 전달하는 DTO
public record AuditLogResponse(
        // 감사 로그 자체의 ID
        Long id,
        // 작업을 수행한 사용자의 ID
        Long userId,
        // 수행한 작업 코드 (예: READ_ENCOUNTER, UPDATE_USER)
        String action,
        // SUCCESS, FAILURE, DENIED 중 하나
        AuditResult result,
        // 실패·거부 사유 코드. 성공이면 null
        String failureReason,
        // 작업 대상 테이블 이름
        String targetTable,
        // 작업 대상 데이터의 ID. 특정 대상이 없으면 null
        Long targetId,
        // 감사 로그가 생성된 시각
        LocalDateTime createdAt
) {
    // DB에서 조회한 엔티티를 응답 DTO로 변환
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(), auditLog.getUserId(), auditLog.getAction(),
                auditLog.getResult(), auditLog.getFailureReason(),
                auditLog.getTargetTable(), auditLog.getTargetId(),
                auditLog.getCreatedAt()
        );
    }
}
