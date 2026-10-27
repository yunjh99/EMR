package com.example.emr.patients.dto;

import com.example.emr.patients.domain.Gender;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 생략한 필드는 유지하고 명시적 null은 거부. 환자 번호는 수정하지 않음
@Getter
@NoArgsConstructor
public class PatientUpdateRequest {

    @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 100, message = "환자명은 100자 이하여야 합니다.")
    @Pattern(regexp = "(?s).*\\S.*", message = "환자명은 공백일 수 없습니다.")
    private String name;

    @JsonSetter(nulls = Nulls.FAIL)
    private LocalDate birthDate;

    @JsonSetter(nulls = Nulls.FAIL)
    private Gender gender;

    @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    @Pattern(regexp = "(?s).*\\S.*", message = "전화번호는 공백일 수 없습니다.")
    private String phone;

    @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    @Pattern(regexp = "(?s).*\\S.*", message = "주소는 공백일 수 없습니다.")
    private String address;
}
