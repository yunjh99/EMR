package com.example.emr.encounters.controller;

import com.example.emr.encounters.dto.EncounterResponse;
import com.example.emr.encounters.dto.EncounterUpdateRequest;
import com.example.emr.encounters.dto.EncounterCreateRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.example.emr.encounter_histories.dto.EncounterHistoryResponse;
import com.example.emr.global.security.Login;
import com.example.emr.users.dto.LoginUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.emr.encounters.service.EncounterService;
import com.example.emr.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/encounters")
@RequiredArgsConstructor
public class EncounterController {
    private final EncounterService encounterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Long> createEncounter(@RequestBody @Valid EncounterCreateRequest request,
                                                  @Login LoginUser loginUser) {
        return SuccessResponse.<Long>builder()
                .status(HttpStatus.CREATED.value())
                .message("진료 기록 생성 성공")
                .data(encounterService.createEncounter(request, loginUser))
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<EncounterResponse> getEncounter(@PathVariable Long id) {
        return SuccessResponse.<EncounterResponse>builder()
                .status(HttpStatus.OK.value())
                .message("진료 기록 조회 성공")
                .data(encounterService.getEncounter(id))
                .build();
    }

    @PatchMapping("/{encounterId}")
    public SuccessResponse<Void> updateEncounter(@PathVariable Long encounterId,
                                                @RequestBody @Valid EncounterUpdateRequest request,
                                                @Login LoginUser loginUser) {
        encounterService.updateEncounter(encounterId, request, loginUser);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("진료 기록 수정 성공")
                .build();
    }

    @GetMapping("/{encounterId}/histories")
    public SuccessResponse<Page<EncounterHistoryResponse>> getEncounterHistories(
            @PathVariable Long encounterId,
            @PageableDefault(size = 20, sort = "version") Pageable pageable) {
        return SuccessResponse.<Page<EncounterHistoryResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("진료 기록 수정 이력 목록 조회 성공")
                .data(encounterService.getEncounterHistories(encounterId, pageable))
                .build();
    }

    @GetMapping("/{encounterId}/histories/{historyId}")
    public SuccessResponse<EncounterHistoryResponse> getEncounterHistory(
            @PathVariable Long encounterId, @PathVariable Long historyId) {
        return SuccessResponse.<EncounterHistoryResponse>builder()
                .status(HttpStatus.OK.value())
                .message("진료 기록 수정 이력 조회 성공")
                .data(encounterService.getEncounterHistory(encounterId, historyId))
                .build();
    }
}
