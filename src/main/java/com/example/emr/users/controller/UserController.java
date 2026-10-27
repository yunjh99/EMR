package com.example.emr.users.controller;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.global.common.SuccessResponse;
import com.example.emr.global.security.Login;
import com.example.emr.users.dto.*;
import com.example.emr.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<Void> createUser(@RequestBody @Valid UserCreateRequest request) {
        userService.createUser(request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.CREATED.value())
                .message("사용자 생성 성공")
                .build();
    }

    @GetMapping("/{id}")
    public SuccessResponse<UserResponse> getUser(@Login LoginUser loginUser, @PathVariable Long id) {
        return SuccessResponse.<UserResponse>builder()
                .status(HttpStatus.OK.value())
                .message("사용자 조회 성공")
                .data(userService.getUser(loginUser, id))
                .build();
    }

    @PatchMapping("/{id}")
    public SuccessResponse<Void> updateUser(
            @PathVariable Long id, @RequestBody @Valid UserUpdateRequest request) {
        userService.updateUser(id, request);
        return SuccessResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("직원 정보 변경 성공")
                .build();
    }

    @GetMapping
    public SuccessResponse<Page<UserListResponse>> getUsers(
            @ModelAttribute UserSearchCondition condition,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return SuccessResponse.<Page<UserListResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("사용자 목록 조회 성공")
                .data(userService.getUsers(condition, pageable))
                .build();
    }

}
