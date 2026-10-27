package com.example.emr.visits.domain;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.global.error.exception.InvalidVisitStatusException;
import com.example.emr.patients.domain.Patient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GeneratedColumn;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "visits",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_one_in_progress_per_doctor",
                        columnNames = {"in_progress_doctor_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // 예약 없이 방문한 접수는 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private VisitType visitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.WAITING;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private LocalDateTime checkedInAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @GeneratedColumn("""
        CASE
            WHEN status = 'IN_PROGRESS'
            THEN doctor_id
            ELSE NULL
        END
        """)
    @Column(
            name = "in_progress_doctor_id",
            insertable = false,
            updatable = false
    )
    private Long inProgressDoctorId;

    public Visit(Patient patient, Appointment appointment, Department department,
                 Doctor doctor, VisitType visitType) {
        this.patient = patient;
        this.appointment = appointment;
        this.department = department;
        this.doctor = doctor;
        this.visitType = visitType;
    }

    public void start() {
        if (this.status != VisitStatus.WAITING) {
            throw new InvalidVisitStatusException();
        }

        this.status = VisitStatus.IN_PROGRESS;
    }

    // 대기 중인 접수만 취소한다. 연결된 예약 상태는 변경하지 않는다.
    public void cancel() {
        if (this.status != VisitStatus.WAITING) {
            throw new InvalidVisitStatusException();
        }
        this.status = VisitStatus.CANCELLED;
    }

    // 진료 완료
    public void complete() {
        if (this.status != VisitStatus.IN_PROGRESS) {
            throw new InvalidVisitStatusException();
        }

        this.status = VisitStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.checkedInAt = now;
        this.createdAt = now;
    }

    // 로그인 사용자가 담당 의사인지 확인
    public boolean isAssignedDoctor(Long userId) {
        return doctor != null
                && doctor.getUser() != null
                && doctor.getUser().getId().equals(userId);
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
