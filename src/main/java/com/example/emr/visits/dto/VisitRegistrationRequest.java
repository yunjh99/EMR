package com.example.emr.visits.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VisitRegistrationRequest(@NotNull @Positive Long patientId,
                                       @NotNull @Positive Long doctorId,
                                       @Positive Long appointmentId) {}
