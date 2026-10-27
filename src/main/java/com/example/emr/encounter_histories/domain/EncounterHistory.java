package com.example.emr.encounter_histories.domain;

import com.example.emr.encounters.domain.Encounter;
import com.example.emr.users.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Table(name = "encounter_histories", uniqueConstraints =
        @UniqueConstraint(name = "uk_encounter_history_version", columnNames = {"encounter_id", "version"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncounterHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_encounter_history_encounter"))
    private Encounter encounter;

    @Column(nullable = false, updatable = false)
    private long version;

    @Column(name = "chief_complaint", columnDefinition = "TEXT", updatable = false)
    private String chiefComplaint;

    @Column(name = "clinical_note", columnDefinition = "TEXT", updatable = false)
    private String clinicalNote;

    @Column(name = "treatment_plan", columnDefinition = "TEXT", updatable = false)
    private String treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_encounter_history_user"))
    private User changedBy;

    @Column(name = "change_reason", nullable = false, length = 500, updatable = false)
    private String changeReason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    // 반드시 원본을 수정하기 전에 생성하여 세 필드 전체와 이전 업무 버전을 보존한다.
    public EncounterHistory(Encounter encounter, User changedBy, String changeReason) {
        this.encounter = encounter;
        this.version = encounter.getVersion();
        this.chiefComplaint = encounter.getChiefComplaint();
        this.clinicalNote = encounter.getClinicalNote();
        this.treatmentPlan = encounter.getTreatmentPlan();
        this.changedBy = changedBy;
        this.changeReason = changeReason;
        this.changedAt = LocalDateTime.now();
    }

    @PreRemove
    private void preventDeletion() {
        throw new UnsupportedOperationException("진료 기록 수정 이력은 삭제할 수 없습니다.");
    }
}
