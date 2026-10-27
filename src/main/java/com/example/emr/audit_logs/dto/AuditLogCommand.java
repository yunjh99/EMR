package com.example.emr.audit_logs.dto;

import com.example.emr.audit_logs.domain.AuditResult;

// Aspect가 수집한 정보를 감사 로그 저장 서비스에 전달하는 내부 DTO
// 화면에서 받는 요청이 아니라 서버가 생성하는 기록 데이터
public record AuditLogCommand(
        // 작업을 수행한 로그인 사용자의 ID
        Long userId,
        // 수행한 작업 코드 (예: READ_ENCOUNTER, UPDATE_USER)
        String action,
        // 작업 대상 테이블 이름 (예: encounters, users)
        String targetTable,
        // 대상 데이터의 ID. 목록 조회처럼 특정 대상이 없으면 null
        Long targetId,
        // 작업 결과: 성공, 실행 실패, 권한 거부
        AuditResult result,
        // 실패·거부 사유 코드 (예: USER_NOT_FOUND, ACCESS_DENIED). 성공이면 null
        String failureReason
) {
}
