package com.example.emr.patients.controller;

import com.example.emr.global.common.SuccessResponse;
import com.example.emr.patients.dto.PatientCreateRequest;
import com.example.emr.patients.dto.PatientSearchCondition;
import com.example.emr.patients.dto.PatientUpdateRequest;
import com.example.emr.patients.dto.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.example.emr.patients.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Long> createPatient(@RequestBody @Valid PatientCreateRequest request) {
        Long id = patientService.createPatient(request);

        return SuccessResponse.<Long>builder()
                .status(HttpStatus.CREATED.value())
                .message("환자 생성 성공")
                .data(id)
                .build();
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public SuccessResponse<Page<PatientResponse>> getPatientsCondition(@ModelAttribute PatientSearchCondition condition,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        return SuccessResponse.<Page<PatientResponse>>builder()
                .status(200)
                .message("환자 목록 조회 성공")
                .data(patientService.getPatientsByCondition(condition, pageable))
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<PatientResponse> getPatient(@PathVariable Long id) {
        return SuccessResponse.<PatientResponse>builder().status(200).message("환자 조회 성공")
                .data(patientService.getPatient(id)).build();
    }

    @PatchMapping("/{id}")
    public SuccessResponse<Void> updatePatient(
            @PathVariable Long id, @RequestBody @Valid PatientUpdateRequest request) {
        patientService.updatePatient(id, request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("환자 정보 수정 성공")
                .build();
    }
}
