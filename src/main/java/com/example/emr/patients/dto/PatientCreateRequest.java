package com.example.emr.patients.dto;

import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientCreateRequest(

        @NotBlank(message = "환자명은 필수입니다.")
        @Size(max = 100, message = "환자명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
        String phone,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birthDate

) {
    public Patient toEntity(String patientNo) {
        return new Patient(
                patientNo,
                name,
                phone,
                address,
                gender,
                birthDate
        );
    }
}