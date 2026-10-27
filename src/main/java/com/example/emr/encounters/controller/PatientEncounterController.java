package com.example.emr.encounters.controller;

import com.example.emr.encounters.dto.EncounterResponse;
import com.example.emr.encounters.service.EncounterService;
import com.example.emr.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients/{patientId}/encounters")
@RequiredArgsConstructor
public class PatientEncounterController {
    private final EncounterService encounterService;

    @GetMapping
    public SuccessResponse<Page<EncounterResponse>> getPatientEncounters(
            @PathVariable Long patientId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return SuccessResponse.<Page<EncounterResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("환자별 진료 기록 목록 조회 성공")
                .data(encounterService.getPatientEncounters(patientId, pageable))
                .build();
    }
}
