package com.ordredesarcane.online.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{3,24}$", message = "Le pseudo doit contenir 3 à 24 caractères alphanumériques ou '_'")
        String username,
        @NotBlank
        @Email
        @Size(max = 160)
        String email,
        @NotBlank
        @Size(min = 8, max = 128, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password
) {
}
