package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.GameMode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartAiMatchRequest(
        @NotNull(message = "mode obligatoire") GameMode mode,
        @NotNull(message = "deckId obligatoire") UUID deckId,
        Faction botFaction,
        AiDifficulty difficulty
) {
}
