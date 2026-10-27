package com.example.emr.visits.controller;

import com.example.emr.global.common.SuccessResponse;
import com.example.emr.global.security.Login;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.visits.service.VisitService;
import com.example.emr.visits.dto.VisitRegistrationRequest;
import com.example.emr.visits.dto.VisitResponse;
import com.example.emr.visits.domain.VisitStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {
    private final VisitService visitService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Long> createVisit(@RequestBody @Valid VisitRegistrationRequest request) {
        return SuccessResponse.<Long>builder()
                .status(HttpStatus.CREATED.value())
                .message("접수 등록 성공")
                .data(visitService.registerVisit(request))
                .build();
    }

    @GetMapping
    public SuccessResponse<Page<VisitResponse>> getVisits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId, @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) VisitStatus status,
            @PageableDefault(size = 20, sort = "checkedInAt", direction = Sort.Direction.ASC) Pageable pageable,
            @Login LoginUser actor) {
        return SuccessResponse.<Page<VisitResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("접수 목록 조회 성공")
                .data(visitService.getVisits(date, doctorId, patientId, status, pageable, actor))
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<VisitResponse> getVisit(@PathVariable Long id, @Login LoginUser actor) {
        return SuccessResponse.<VisitResponse>builder()
                .status(HttpStatus.OK.value())
                .message("접수 조회 성공")
                .data(visitService.getVisit(id, actor))
                .build();
    }

    @PatchMapping("/{id}/cancel")
    public SuccessResponse<Void> cancelVisit(@PathVariable Long id) {
        visitService.cancelVisit(id);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("접수 취소 성공")
                .build();
    }

    @PatchMapping("/{id}/start")
    public SuccessResponse<Void> startVisit(@PathVariable Long id, @Login LoginUser loginUser) {
        visitService.startVisit(id, loginUser);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("진료 시작 성공")
                .build();
    }
}
