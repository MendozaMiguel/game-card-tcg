package com.ordredesarcane.online.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record JwtProperties(
        String jwtSecret,
        long jwtExpirationSeconds,
        String cookieName,
        boolean cookieSecure
) {
    public JwtProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalArgumentException(
                    "ARCANE_JWT_SECRET must be at least 64 bytes (512 bits) for HS512.");
        }
        if (jwtExpirationSeconds <= 0) {
            jwtExpirationSeconds = 86400;
        }
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "ARCANE_SESSION";
        }
    }
}
