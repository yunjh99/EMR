package com.example.emr.encounters.domain;

import com.example.emr.doctors.domain.Doctor;
import com.example.emr.visits.domain.Visit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "encounters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 진료 기록에 연결된 방문 접수
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    // 주호소: 환자가 방문한 주된 이유나 호소하는 증상 (예: 기침, 발열)
    @Column(name = "chief_complaint", columnDefinition = "TEXT", nullable = false)
    private String chiefComplaint;

    // 진료 소견: 의사가 문진·진찰 결과와 의학적 판단을 기록한 내용
    @Column(name = "clinical_note", columnDefinition = "TEXT", nullable = false)
    private String clinicalNote;

    // 치료 계획: 처방, 추가 검사, 치료 및 추후 방문 계획
    @Column(name = "treatment_plan", columnDefinition = "TEXT", nullable = false)
    private String treatmentPlan;

    // 진료 기록의 업무 버전. JPA 낙관적 락용 @Version이 아니다.
    @Column(nullable = false)
    private long version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Encounter(Visit visit, String chiefComplaint,
                     String clinicalNote, String treatmentPlan) {
        this.visit = visit;
        this.chiefComplaint = chiefComplaint;
        this.clinicalNote = clinicalNote;
        this.treatmentPlan = treatmentPlan;
    }

    // 진료 기록 수정
    public void update(String chiefComplaint, String clinicalNote, String treatmentPlan) {
        if (!hasChanges(chiefComplaint, clinicalNote, treatmentPlan)) {
            return;
        }
        this.chiefComplaint = Objects.requireNonNullElse(chiefComplaint, this.chiefComplaint);
        this.clinicalNote = Objects.requireNonNullElse(clinicalNote, this.clinicalNote);
        this.treatmentPlan = Objects.requireNonNullElse(treatmentPlan, this.treatmentPlan);
        this.version = Math.incrementExact(this.version);
    }

    public boolean hasChanges(String chiefComplaint, String clinicalNote, String treatmentPlan) {
        return (chiefComplaint != null && !Objects.equals(chiefComplaint, this.chiefComplaint))
                || (clinicalNote != null && !Objects.equals(clinicalNote, this.clinicalNote))
                || (treatmentPlan != null && !Objects.equals(treatmentPlan, this.treatmentPlan));
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
