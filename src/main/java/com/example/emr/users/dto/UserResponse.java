package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;

public record UserResponse(
        Long id,
        String username,
        String name,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole());
    }
}
