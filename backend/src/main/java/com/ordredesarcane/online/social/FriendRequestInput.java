package com.ordredesarcane.online.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FriendRequestInput(
        @NotBlank @Size(min = 3, max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "Nom d'utilisateur invalide.")
        String username
) {
}
