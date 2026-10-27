package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {

    @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    @Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백일 수 없습니다.")
    private String name;

    @JsonSetter(nulls = Nulls.FAIL)
    private Role role;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean active;
}
