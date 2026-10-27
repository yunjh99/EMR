package com.example.emr.global.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private final JwtProvider provider = provider("01234567890123456789012345678901", 60_000);

    @Test
    void accessTokenAuthenticatesUsername() {
        var token = provider.createToken("tester");
        var authentication = provider.getAuthentication(token.getAccessToken().getData());

        assertTrue(authentication.isAuthenticated());
        assertEquals("tester", authentication.getName());
        assertTrue(authentication.getAuthorities().isEmpty());
        assertTrue(provider.isRefreshTokenValid(token.getRefreshToken().getData()));
    }

    @Test
    void accessAndRefreshTokensCannotBeInterchanged() {
        var token = provider.createToken("tester");

        assertThrows(JwtException.class,
                () -> provider.getAuthentication(token.getRefreshToken().getData()));
        assertFalse(provider.isRefreshTokenValid(token.getAccessToken().getData()));
    }

    @Test
    void rejectsExpiredToken() {
        var expired = provider("01234567890123456789012345678901", -60_000)
                .createToken("tester").getAccessToken().getData();

        assertThrows(JwtException.class, () -> provider.getAuthentication(expired));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        var forged = provider("abcdefghijklmnopqrstuvwxyz012345", 60_000)
                .createToken("tester").getAccessToken().getData();

        assertThrows(JwtException.class, () -> provider.getAuthentication(forged));
    }

    private static JwtProvider provider(String key, long accessExpiration) {
        return new JwtProvider(key, accessExpiration, 120_000, "Authorization", "Authorization-refresh");
    }
}
