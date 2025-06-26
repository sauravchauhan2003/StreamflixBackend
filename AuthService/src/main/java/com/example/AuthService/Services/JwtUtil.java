package com.example.AuthService.Services;

import com.example.AuthService.Model.MyUser;
import com.example.AuthService.Model.MyUserRepository;
import com.example.AuthService.Model.Role;
import io.jsonwebtoken.*;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Optional;

import static com.example.AuthService.Model.Role.USER;

@Component
public class JwtUtil {

    private static final String SECRET = "MySuperSecretKeyMySuperSecretKey1234"; // Must be at least 32 chars
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 10*24;

    private static Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }
    @Autowired
    private MyUserRepository repository;
    // 1. Generate JWT token
    public  String generateToken(MyUser user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("email", user.getEmail())
                .claim("password", user.getPassword()) // ❗Only for testing!
                .claim("role", user.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Validate token and check user exists
    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String username = claims.getSubject();
            String email = claims.get("email", String.class);

            Optional<MyUser> userOpt = repository.findByUsername(username);
            return userOpt.isPresent() && userOpt.get().isEnabled();
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 3. Extract MyUser object from token
    public MyUser extractUser(String token) {
        Claims claims = extractAllClaims(token);
        return repository.findByUsername(claims.getSubject()).get();
    }

    // Helper: Extract all claims
    private  Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
