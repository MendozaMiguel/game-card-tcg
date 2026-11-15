package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.GameMode;
import java.time.Instant;
import java.util.UUID;

public record LobbyNotificationDto(
        String eventType,
        UUID matchId,
        GameMode mode,
        boolean ranked,
        String message,
        Instant timestamp
) {
}
