package com.example.API.Gateway;

/**
 * Previously the Spring MVC Security config.
 * JWT authentication is now handled by JwtGlobalFilter (a Spring Cloud Gateway GlobalFilter).
 * CORS is now handled by GatewayCorsConfig.
 * This file is kept as an empty stub to avoid compilation errors during migration.
 */
public class SecurityConfig {
    // Intentionally empty — replaced by JwtGlobalFilter and GatewayCorsConfig
}
