package com.example.emr.users.dto;

public record AuthResponse(UserResponse user, String accessToken, long expiresIn) {}
