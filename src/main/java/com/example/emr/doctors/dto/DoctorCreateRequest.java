package com.example.emr.doctors.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record DoctorCreateRequest(
        @Positive Long userId,
        @Valid DoctorAccountRequest account,
        @NotNull @Positive Long departmentId,
        @NotBlank @Size(max = 30) @Pattern(regexp = "\\S+") String licenseNo,
        @Size(max = 100) String specialty
) {
    @JsonIgnore
    @AssertTrue(message = "기존 userId 또는 신규 account 중 하나만 입력해야 합니다.")
    public boolean isAccountSelectionValid() { return (userId != null) != (account != null); }
}
