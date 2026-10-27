package com.example.emr.appointments.controller;

import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.appointments.dto.AppointmentResponse;
import com.example.emr.appointments.domain.AppointmentStatus;
import com.example.emr.appointments.service.AppointmentService;
import com.example.emr.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Void> createAppointment(@RequestBody @Valid AppointmentCreateRequest request) {
        appointmentService.createAppointment(request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.CREATED.value())
                .message("예약 생성 성공")
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<AppointmentResponse> getAppointment(@PathVariable Long id) {
        return SuccessResponse.<AppointmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("예약 조회 성공")
                .data(appointmentService.getAppointment(id))
                .build();
    }

    @GetMapping
    public SuccessResponse<Page<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status,
            @PageableDefault(size = 20, sort = {"scheduledAt", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return SuccessResponse.<Page<AppointmentResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("예약 목록 조회 성공")
                .data(appointmentService.getAppointments(date, doctorId, status, pageable))
                .build();
    }

    @PatchMapping("/{id}/cancel")
    public SuccessResponse<Void> cancelAppointment(@PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("예약 취소 성공")
                .build();
    }

}
