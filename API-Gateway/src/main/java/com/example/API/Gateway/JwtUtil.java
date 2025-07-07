package com.example.API.Gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.security.Key;

public class JwtUtil {

    private static final String SECRET = "MySuperSecretKeyMySuperSecretKey1234";

    private static Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token); // ✅ signature & expiration are verified here
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
