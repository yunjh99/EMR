package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "로그인 ID는 필수입니다.")
        @Size(max = 20, message = "로그인 ID는 20자 이하여야 합니다.")
        @Pattern(regexp = "\\S+", message = "로그인 ID에는 공백을 사용할 수 없습니다.")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "역할은 필수입니다.")
        Role role
) {

    public User toEntity(String encodedPassword) {
        return new User(
                username,
                encodedPassword,
                name,
                role
        );
    }
}
