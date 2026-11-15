package com.ordredesarcane.online.lobby.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatMessageCommand(
        UUID matchId,
        boolean teamOnly,
        @NotBlank(message = "Message vide.")
        @Size(min = 1, max = 300, message = "Message limité à 300 caractères.")
        String content
) {
}
