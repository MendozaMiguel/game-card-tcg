package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.TeamId;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record JoinCustomRoomRequest(
        UUID deckId,
        @NotNull TeamId preferredTeam,
        boolean asSpectator
) {
}
