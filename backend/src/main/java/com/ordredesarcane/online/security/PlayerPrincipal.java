package com.ordredesarcane.online.security;

import java.security.Principal;
import java.util.UUID;

public record PlayerPrincipal(UUID userId, String username, String role) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
