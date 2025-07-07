package com.example.API.Gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JWTAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow unauthenticated access to /auth/**
        if (path.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = request.getHeader("Authorization");

        // ✅ Validate header presence and prefix
        if (jwt == null || !jwt.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized request: Missing or invalid Authorization header");
            return;
        }

        // ✅ Remove "Bearer " prefix
        String token = jwt.substring(7);

        if (jwtUtil.validateToken(token)) {
            // Token is valid — allow request to proceed
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized request: Invalid or expired token");
        }
    }
}
