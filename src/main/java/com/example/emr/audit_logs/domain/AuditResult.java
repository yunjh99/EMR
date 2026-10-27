package com.example.emr.audit_logs.domain;

// 감사 대상 작업의 처리 결과
public enum AuditResult {
    SUCCESS, // 정상 완료
    FAILURE, // 작업 도중 오류 발생 (예: 조회 대상 없음, DB 오류)
    DENIED   // 권한이 없어 작업 거부
}
