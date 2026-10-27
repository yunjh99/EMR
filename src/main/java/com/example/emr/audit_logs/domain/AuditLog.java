package com.example.emr.audit_logs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// audit_logs 테이블에 저장되는 감사 기록 한 건
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    // 감사 로그 자체의 식별자. DB의 AUTO_INCREMENT로 생성
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작업을 수행한 사용자 ID (작업 대상 사용자 ID와 구분)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 수행한 작업 코드 (예: READ_ENCOUNTER, UPDATE_USER)
    @Column(name = "action", length = 30, nullable = false)
    private String action;

    // 작업 결과를 enum 이름 그대로 저장 (예: SUCCESS)
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private AuditResult result;

    // 예외 원문 대신 USER_NOT_FOUND, ACCESS_DENIED 등의 사유 코드만 기록. 성공이면 null
    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    // 작업 대상 테이블 이름 (예: users, encounters)
    @Column(name = "target_table", length = 100, nullable = false)
    private String targetTable;

    // 대상 테이블에 있는 데이터의 ID. 목록 조회 등은 null 가능
    @Column(name = "target_id")
    private Long targetId;

    // 감사 로그 생성 시각. 저장 후에는 수정하지 않음
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog(Long userId, String action, String targetTable, Long targetId,
                    AuditResult result, String failureReason) {
        this.userId = userId;
        this.action = action;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.result = result;
        this.failureReason = failureReason;
    }

    // JPA가 처음 저장하기 직전에 생성 시각을 설정
    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
