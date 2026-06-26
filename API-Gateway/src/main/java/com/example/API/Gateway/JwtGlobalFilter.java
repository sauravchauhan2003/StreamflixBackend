package com.example.API.Gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter for the Spring Cloud Gateway:
 *  1. Injects X-Internal-Key header into EVERY forwarded request so that
 *     microservices can reject direct (non-gateway) traffic.
 *  2. Validates JWT Bearer token for protected endpoints.
 *     Public paths (auth, HLS segments, thumbnails, video listings, analytics) bypass JWT check.
 */
@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    @Value("${app.internal-key}")
    private String internalKey;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // ── Step 1: Inject internal key into every forwarded request ──────────
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Internal-Key", internalKey)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // ── Step 2: Determine if JWT is required ──────────────────────────────
        if (isPublicPath(path)) {
            return chain.filter(mutatedExchange);
        }

        // ── Step 3: Validate JWT ──────────────────────────────────────────────
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange, "Invalid or expired JWT token");
        }

        return chain.filter(mutatedExchange);
    }

    /**
     * Returns true for paths that do NOT require a JWT token.
     * - Auth service routes (/auth/**)
     * - HLS manifest + segment files (.m3u8, .ts)
     * - Video thumbnails
     * - Public video listing and search
     * - Analytics (read-only stats)
     */
    private boolean isPublicPath(String path) {
        if (path.startsWith("/auth/")) return true;
        if (path.endsWith(".m3u8") || path.endsWith(".ts")) return true;
        if (path.contains("/thumbnails/")) return true;
        // Public video browsing
        if (path.equals("/video/videos")) return true;
        if (path.startsWith("/video/videos/") && !path.endsWith("/like")
                && !path.endsWith("/dislike") && !path.endsWith("/interaction")) return true;
        if (path.startsWith("/video/analytics")) return true;
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        var body = ("{\"error\": \"" + message + "\"}").getBytes();
        var buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Run before all other filters
    }
}
