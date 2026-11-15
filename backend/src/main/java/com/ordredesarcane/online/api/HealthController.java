package com.ordredesarcane.online.api;

import com.ordredesarcane.online.api.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;
    private final boolean virtualThreadsEnabled;

    public HealthController(
            @Value("${spring.application.name:arcane-online-backend}") String applicationName,
            @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
        this.applicationName = applicationName;
        this.virtualThreadsEnabled = virtualThreadsEnabled;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName,
                virtualThreadsEnabled,
                System.getProperty("java.version"));
    }
}
