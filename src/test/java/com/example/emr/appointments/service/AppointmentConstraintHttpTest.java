package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.AppointmentConstraintViolations;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.appointments.repository.AppointmentRepository;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.global.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppointmentConstraintHttpTest {
    @Test void activeSlotConstraintIsReturnedAs409() throws Exception {
        perform(1062, "Duplicate entry '1-2026' for key 'appointments.uk_active_doctor_slot'",
                "해당 의사의 같은 시간에 이미 예약이 존재합니다.");
    }
    @Test void unrelatedUniqueIsNotClassifiedAsAppointmentConflict() throws Exception {
        perform(1062, "Duplicate entry 'x' for key 'users.uk_users_username'",
                "데이터 참조 또는 중복 제약조건을 확인해 주세요.");
    }
    @Test void foreignKeyIsNotClassifiedAsAppointmentConflict() throws Exception {
        perform(1452, "Cannot add or update a child row: a foreign key constraint fails",
                "데이터 참조 또는 중복 제약조건을 확인해 주세요.");
    }
    @Test void textInsideDuplicateValueCannotSpoofIndexName() {
        assertThat(AppointmentConstraintViolations.isActiveSlotDuplicate(new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'for key 'uk_active_doctor_slot'' for key 'other_index'", "23000", 1062))).isFalse();
    }
    private void perform(int code, String message, String expected) throws Exception {
        var repository = mock(AppointmentRepository.class);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("integrity",
                new SQLIntegrityConstraintViolationException(message, "23000", code)));
        var doctor = mock(Doctor.class);
        when(doctor.getId()).thenReturn(1L);
        var request = new AppointmentCreateRequest(doctor, null, LocalDateTime.of(2030, 1, 1, 9, 0), "test");
        var slots = mock(com.example.emr.appointments.repository.AppointmentSlotRepository.class);
        when(slots.findForUpdate(any(), any())).thenReturn(java.util.Optional.of(
                mock(com.example.emr.appointments.domain.AppointmentSlot.class)));
        var endpoint = new Endpoint(new AppointmentService(repository, slots), request);
        MockMvcBuilders.standaloneSetup(endpoint).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(post("/test/appointment-conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(expected));
    }
    @RestController
    static class Endpoint {
        private final AppointmentService service;
        private final AppointmentCreateRequest request;
        Endpoint(AppointmentService service, AppointmentCreateRequest request) { this.service = service; this.request = request; }
        @PostMapping("/test/appointment-conflict") Long create() { return service.createAppointment(request); }
    }
}
