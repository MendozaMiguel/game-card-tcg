package com.ordredesarcane.online.api.dto;

public record HealthResponse(
        String status,
        String application,
        boolean virtualThreadsEnabled,
        String javaVersion
) {
}
