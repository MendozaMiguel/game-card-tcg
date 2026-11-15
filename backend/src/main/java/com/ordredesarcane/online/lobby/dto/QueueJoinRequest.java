package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.GameMode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record QueueJoinRequest(
        @NotNull(message = "mode obligatoire") GameMode mode,
        boolean ranked,
        @NotNull(message = "deckId obligatoire") UUID deckId
) {
}
