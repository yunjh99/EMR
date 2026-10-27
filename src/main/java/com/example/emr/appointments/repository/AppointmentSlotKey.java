package com.example.emr.appointments.repository;

import java.time.LocalDateTime;

/** Scalar projection: do not cache an appointment's state before acquiring its slot lock. */
public record AppointmentSlotKey(Long doctorId, LocalDateTime scheduledAt) {}
