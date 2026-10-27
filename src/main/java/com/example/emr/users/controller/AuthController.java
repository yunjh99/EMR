package com.example.emr.users.controller;

import com.example.emr.global.common.SuccessResponse;
import com.example.emr.global.security.JwtProvider;
import com.example.emr.global.security.RefreshToken;
import com.example.emr.users.dto.AuthResponse;
import com.example.emr.users.dto.LoginRequest;
import com.example.emr.users.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final JwtProvider jwt;
    @Value("${auth.refresh-cookie.secure:false}") private boolean secure;

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        return authenticated(auth.login(request));
    }

    @PostMapping("/reissue")
    public ResponseEntity<SuccessResponse<AuthResponse>> reissue(RefreshToken refreshToken) {
        return authenticated(auth.reissue(refreshToken.getData()));
    }

    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<Void>> logout() {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(SuccessResponse.<Void>builder().status(200).message("로그아웃 성공").build());
    }

    private ResponseEntity<SuccessResponse<AuthResponse>> authenticated(AuthService.AuthenticatedUser result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie(result.token().getRefreshToken().getData(),
                        Duration.ofMillis(jwt.getRefreshTokenExpirationPeriod())).toString())
                .body(SuccessResponse.<AuthResponse>builder().status(200).message("인증 성공")
                        .data(new AuthResponse(result.user(), result.token().getAccessToken().getData(),
                                jwt.getAccessTokenExpirationPeriod() / 1000)).build());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwt.getRefreshHeader(), value).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/api/reissue").maxAge(maxAge).build();
    }
}
