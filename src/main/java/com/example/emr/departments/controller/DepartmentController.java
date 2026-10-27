package com.example.emr.departments.controller;

import com.example.emr.departments.dto.*;
import com.example.emr.departments.service.DepartmentService;
import com.example.emr.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Void> createDepartment(@RequestBody @Valid DepartmentRequest request) {
        departmentService.create(request);

        return SuccessResponse.<Void>builder()
                .status(HttpStatus.CREATED.value())
                .message("진료과 등록 성공")
                .build();
    }

    @GetMapping
    public SuccessResponse<List<DepartmentResponse>> getDepartments(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return SuccessResponse.<List<DepartmentResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("진료과 목록 조회 성공")
                .data(departmentService.list(includeInactive))
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<DepartmentResponse> getDepartment(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return SuccessResponse.<DepartmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("진료과 조회 성공")
                .data(departmentService.get(id, includeInactive))
                .build();
    }

    @PutMapping("/{id}")
    public SuccessResponse<Void> updateDepartment(
            @PathVariable Long id, @RequestBody @Valid DepartmentRequest request) {
        departmentService.update(id, request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("진료과 수정 성공")
                .build();
    }

    @PatchMapping("/{id}/deactivate")
    public SuccessResponse<Void> deactivateDepartment(@PathVariable Long id) {
        departmentService.deactivate(id);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("진료과 비활성화 성공")
                .build();
    }
}
