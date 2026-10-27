package com.example.emr.doctors.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record DoctorAccountRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "\\S+") String username,
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 100) String name
) {
    @JsonIgnore
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordLengthValid() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
