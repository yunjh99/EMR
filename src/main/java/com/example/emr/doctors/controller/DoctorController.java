package com.example.emr.doctors.controller;

import com.example.emr.doctors.dto.*;
import com.example.emr.doctors.service.DoctorService;
import com.example.emr.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Void> createDoctor(@RequestBody @Valid DoctorCreateRequest request) {
        doctorService.create(request);

        return SuccessResponse.<Void>builder()
                .status(HttpStatus.CREATED.value())
                .message("의사 등록 성공")
                .build();
    }

    @GetMapping
    public SuccessResponse<List<DoctorResponse>> getDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return SuccessResponse.<List<DoctorResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("의사 목록 조회 성공")
                .data(doctorService.list(departmentId, includeInactive))
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<DoctorResponse> getDoctor(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return SuccessResponse.<DoctorResponse>builder()
                .status(HttpStatus.OK.value())
                .message("의사 조회 성공")
                .data(doctorService.get(id, includeInactive))
                .build();
    }

    @PutMapping("/{id}")
    public SuccessResponse<Void> updateDoctor(
            @PathVariable Long id, @RequestBody @Valid DoctorUpdateRequest request) {
        doctorService.update(id, request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("의사 수정 성공")
                .build();
    }

    @PatchMapping("/{id}/deactivate")
    public SuccessResponse<Void> deactivateDoctor(@PathVariable Long id) {
        doctorService.deactivate(id);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("의사 비활성화 성공")
                .build();
    }
}
