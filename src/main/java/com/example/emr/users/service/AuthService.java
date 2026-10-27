package com.example.emr.users.service;

import com.example.emr.global.error.exception.InvalidCredentialsException;
import com.example.emr.global.security.JwtProvider;
import com.example.emr.global.security.Token;
import com.example.emr.users.dto.LoginRequest;
import com.example.emr.users.dto.UserResponse;
import com.example.emr.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtProvider jwt;

    @Transactional
    public AuthenticatedUser login(LoginRequest request) {
        var user = users.findByUsername(request.username()).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.recordLogin();
        return new AuthenticatedUser(UserResponse.from(user), jwt.createToken(user.getUsername()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser reissue(String refreshToken) {
        if (!jwt.isRefreshTokenValid(refreshToken)) throw new InvalidCredentialsException();
        var user = users.findByUsername(jwt.getUsernameFromToken(refreshToken)).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) throw new InvalidCredentialsException();
        return new AuthenticatedUser(UserResponse.from(user), jwt.createToken(user.getUsername()));
    }

    public record AuthenticatedUser(UserResponse user, Token token) {}
}
