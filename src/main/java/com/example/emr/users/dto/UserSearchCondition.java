package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;

public record UserSearchCondition(
        String username,
        String name,
        Role role,
        Boolean active
) {
    public UserSearchCondition {
        username = normalize(username);
        name = normalize(name);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
