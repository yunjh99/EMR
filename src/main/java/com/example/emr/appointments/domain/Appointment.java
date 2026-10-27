package com.example.emr.appointments.domain;

import com.example.emr.doctors.domain.Doctor;
import com.example.emr.patients.domain.Patient;
import com.example.emr.global.error.exception.AppointmentCancellationNotAllowedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "appointments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_doctor_scheduled_at",
                        columnNames = {"doctor_id", "scheduled_at"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예약 담당 의사
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // 예약 환자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Appointment(Doctor doctor, Patient patient, LocalDateTime scheduledAt, String reason) {
        this.doctor = doctor;
        this.patient = patient;
        this.scheduledAt = scheduledAt;
        this.reason = reason;
    }

    public void cancel() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new AppointmentCancellationNotAllowedException();
        }
        this.status = AppointmentStatus.CANCELLED;
    }

    public void checkIn() {
        if (this.status != AppointmentStatus.SCHEDULED) {
            throw new IllegalStateException("접수 가능한 예약 상태가 아닙니다.");
        }

        this.status = AppointmentStatus.CHECKED_IN;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
