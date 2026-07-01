package com.chefit.auth.service;

import com.chefit.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32B";

    private User user() {
        return new User("u1", "a@b.com", true, null, "g1",
                List.of("google"), "Ada", "127.0.0.1", null, null);
    }

    @Test
    void issuedTokenParsesBackToClaims() {
        JwtService service = new JwtService(SECRET, 86400000L);

        String token = service.issue(user());
        Claims claims = service.parse(token);

        assertEquals("u1", claims.getSubject());
        assertEquals("a@b.com", claims.get("identifier"));
        assertEquals("Ada", claims.get("name"));
        assertNotNull(claims.getExpiration());
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = new JwtService(SECRET, 86400000L).issue(user());
        JwtService other = new JwtService("another-secret-another-secret-32-bytes!", 86400000L);

        assertThrows(JwtException.class, () -> other.parse(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1000L);
        String token = service.issue(user());

        assertThrows(JwtException.class, () -> service.parse(token));
    }
}
