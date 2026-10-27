package com.example.emr.users.dto;

import com.example.emr.users.domain.Role;

import java.security.Principal;

public record LoginUser(Long userId, Role role) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
