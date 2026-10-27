package com.example.emr.appointments.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "appointment_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appointment_slot",
                        columnNames = {"doctor_id", "slot_at"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "slot_at", nullable = false)
    private LocalDateTime slotAt;
}