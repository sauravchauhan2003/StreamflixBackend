package com.example.API.Gateway;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Key;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JWT-aware leaky bucket rate limiter implemented as a Spring Cloud Gateway GlobalFilter.
 *
 * <p>Two bucket configurations:</p>
 * <ul>
 *   <li><b>Normal endpoints</b>: configurable (default 100 req/min)</li>
 *   <li><b>Streaming endpoints</b> (.m3u8, .ts, /thumbnails/): configurable (default 10 req/sec)</li>
 * </ul>
 *
 * <p>The bucket key is extracted from the JWT token (username) when available,
 * otherwise falls back to the client's IP address. This ensures per-user rate limiting
 * for authenticated users and per-IP limiting for anonymous traffic.</p>
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "MySuperSecretKeyMySuperSecretKey1234";

    // Separate bucket caches for normal and streaming limits
    private final Map<String, Bucket> normalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> streamingBuckets = new ConcurrentHashMap<>();

    @Value("${ratelimit.normal.capacity:100}")
    private long normalCapacity;

    @Value("${ratelimit.normal.refill-tokens:100}")
    private long normalRefillTokens;

    @Value("${ratelimit.normal.refill-seconds:60}")
    private long normalRefillSeconds;

    @Value("${ratelimit.streaming.capacity:10}")
    private long streamingCapacity;

    @Value("${ratelimit.streaming.refill-tokens:10}")
    private long streamingRefillTokens;

    @Value("${ratelimit.streaming.refill-seconds:1}")
    private long streamingRefillSeconds;

    public RateLimitFilter() {
        // Periodic cleanup of expired/stale buckets to prevent memory leaks (every 5 min)
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            normalBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() >= normalCapacity);
            streamingBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() >= streamingCapacity);
        }, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String bucketKey = resolveBucketKey(exchange);
        boolean isStreaming = isStreamingPath(path);

        Bucket bucket = isStreaming
                ? streamingBuckets.computeIfAbsent(bucketKey, k -> createStreamingBucket())
                : normalBuckets.computeIfAbsent(bucketKey, k -> createNormalBucket());

        if (bucket.tryConsume(1)) {
            // Request allowed — add remaining-limit header for transparency
            long remaining = bucket.getAvailableTokens();
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));
            return chain.filter(exchange);
        }

        // Rate limit exceeded → 429 Too Many Requests
        return tooManyRequests(exchange, isStreaming);
    }

    /**
     * Determines the bucket key: JWT username if present, else client IP.
     */
    private String resolveBucketKey(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                String username = claims.getSubject();
                if (username != null && !username.isBlank()) {
                    return "user:" + username;
                }
            } catch (Exception ignored) {
                // Invalid token — fall through to IP-based limiting
            }
        }
        // Fallback: use client IP address
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String ip = (remoteAddress != null) ? remoteAddress.getAddress().getHostAddress() : "unknown";
        return "ip:" + ip;
    }

    /**
     * Returns true for streaming-related paths that get the higher-frequency limit.
     */
    private boolean isStreamingPath(String path) {
        return path.endsWith(".m3u8")
                || path.endsWith(".ts")
                || path.contains("/thumbnails/");
    }

    private Bucket createNormalBucket() {
        Bandwidth limit = Bandwidth.classic(
                normalCapacity,
                Refill.greedy(normalRefillTokens, Duration.ofSeconds(normalRefillSeconds))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createStreamingBucket() {
        Bandwidth limit = Bandwidth.classic(
                streamingCapacity,
                Refill.greedy(streamingRefillTokens, Duration.ofSeconds(streamingRefillSeconds))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, boolean isStreaming) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        long retryAfter = isStreaming ? streamingRefillSeconds : normalRefillSeconds;
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfter));
        String body = "{\"error\": \"Rate limit exceeded. Please slow down.\", \"retryAfter\": " + retryAfter + "}";
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -2; // Run BEFORE JwtGlobalFilter (order -1) so rate limiting is enforced first
    }
}
