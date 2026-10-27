package com.example.emr.patient_histories.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 환자 정보의 변경 이력. 변경한 필드마다 한 건씩 기록
@Entity
@Table(name = "patient_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 정보가 변경된 환자의 ID
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    // 해당 변경 작업을 기록한 감사 로그의 ID
    @Column(name = "audit_log_id", nullable = false)
    private Long auditLogId;

    // 변경된 필드 이름 (예: phone, address)
    @Column(name = "field_name", length = 50, nullable = false)
    private String fieldName;

    // 변경 전 값. 기존 값이 없으면 null
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    // 변경 후 값. 값을 제거한 경우 null 가능
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;



    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public PatientHistory(Long patientId, Long auditLogId, String fieldName,
                          String oldValue, String newValue) {
        this.patientId = patientId;
        this.auditLogId = auditLogId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    private void prePersist() {
        changedAt = LocalDateTime.now();
    }
}
