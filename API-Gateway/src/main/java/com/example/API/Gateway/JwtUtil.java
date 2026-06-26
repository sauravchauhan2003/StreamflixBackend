package com.example.API.Gateway;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;

/**
 * Stateless JWT validation utility for the API Gateway.
 * Validates signature and expiry only — no DB lookup needed.
 */
@Component
public class JwtUtil {

    private static final String SECRET = "MySuperSecretKeyMySuperSecretKey1234"; // 36 chars → HS256

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token); // also verifies expiry
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
