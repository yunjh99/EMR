package com.example.emr.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Getter
public class JwtProvider {

    private static final String BEARER = "Bearer ";
    private final SecretKey secretKey;
    private final long accessTokenExpirationPeriod;
    private final long refreshTokenExpirationPeriod;
    private final String accessHeader;
    private final String refreshHeader;

    public JwtProvider(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpirationPeriod,
            @Value("${jwt.refresh-token-expiration:1209600000}") long refreshTokenExpirationPeriod,
            @Value("${jwt.access-header:Authorization}") String accessHeader,
            @Value("${jwt.refresh-header:Authorization-refresh}") String refreshHeader) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationPeriod = accessTokenExpirationPeriod;
        this.refreshTokenExpirationPeriod = refreshTokenExpirationPeriod;
        this.accessHeader = accessHeader;
        this.refreshHeader = refreshHeader;
    }

    public Token createToken(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        return Token.builder()
                .accessToken(new AccessToken(accessHeader, createToken(username, "access", accessTokenExpirationPeriod)))
                .refreshToken(new RefreshToken(refreshHeader, createToken(username, "refresh", refreshTokenExpirationPeriod)))
                .build();
    }

    public Optional<String> extractBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(accessHeader))
                .filter(token -> token.startsWith(BEARER))
                .map(token -> token.substring(BEARER.length()))
                .filter(token -> !token.isBlank());
    }

    public String getUsernameFromToken(String token) {
        return extractToken(token).getSubject();
    }

    // Validates signature and expiration for either token kind.
    public boolean isTokenValid(String token) {
        try {
            extractToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            requireTokenKind(extractToken(token), "refresh");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = extractToken(token);
        requireTokenKind(claims, "access");
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of());
    }

    public Claims extractToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void requireTokenKind(Claims claims, String kind) {
        if (!kind.equals(claims.get("token_kind", String.class))
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getExpiration() == null) {
            throw new JwtException("Invalid token kind or required claims");
        }
    }

    private String createToken(String username, String kind, long expirationPeriod) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("token_kind", kind)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationPeriod))
                .signWith(secretKey)
                .compact();
    }
}
