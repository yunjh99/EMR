package com.example.emr.users.service;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.global.error.exception.DuplicateUserNameException;
import com.example.emr.global.error.exception.UserNotFoundException;
import com.example.emr.users.domain.Role;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.UserCreateRequest;
import com.example.emr.users.dto.UserListResponse;
import com.example.emr.users.dto.UserResponse;
import com.example.emr.users.dto.UserSearchCondition;
import com.example.emr.users.dto.UserUpdateRequest;
import com.example.emr.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    // 유저 생성
    @Audit(
            action = "CREATE",
            targetTable = "users",
            targetId = "#result"
    )
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Long createUser(UserCreateRequest request){
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserNameException();
        }
        User user = request.toEntity(passwordEncoder.encode(request.password()));
        return userRepository.save(user).getId();
    }

    // 유저 단건 조회
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'STAFF')")
    public UserResponse getUser(LoginUser loginUser, Long userId) {

        if (loginUser.role() != Role.ADMIN && !loginUser.userId().equals(userId)) {
            throw new AccessDeniedException("본인 정보만 조회할 수 있습니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    // 유저 정보 수정
    @Audit(
            action = "UPDATE_USER",
            targetTable = "users",
            targetId = "#userId"
    )
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.update(request.getName(), request.getRole(), request.getActive());
    }

    // 유저 목록 조회
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserListResponse> getUsers(UserSearchCondition condition, Pageable pageable) {
        return userRepository.search(condition, pageable).map(UserListResponse::from);
    }

}
