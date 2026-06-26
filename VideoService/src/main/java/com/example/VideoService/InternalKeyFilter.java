package com.example.VideoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter ensuring VideoService only handles requests from the API Gateway.
 * The gateway injects X-Internal-Key before forwarding; all other requests are blocked.
 */
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    @Value("${app.internal-key}")
    private String expectedKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Allow H2 console for local dev
        if (request.getRequestURI().startsWith("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }

        String receivedKey = request.getHeader("X-Internal-Key");
        if (expectedKey.equals(receivedKey)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Direct access not allowed. Use the API Gateway on port 9010.\"}");
        }
    }
}
