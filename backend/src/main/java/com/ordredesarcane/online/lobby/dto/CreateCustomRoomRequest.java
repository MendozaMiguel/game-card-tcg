package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.GameMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCustomRoomRequest(
        @NotBlank @Size(min = 2, max = 48) String name,
        @NotNull GameMode mode,
        @NotNull UUID deckId,
        boolean allowSpectators
) {
}
