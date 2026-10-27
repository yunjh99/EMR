package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;

public record UserListResponse(
        Long id,
        String username,
        String name,
        Role role,
        boolean active
) {
    public static UserListResponse from(User user) {
        return new UserListResponse(user.getId(), user.getUsername(), user.getName(),
                user.getRole(), user.isActive());
    }
}
