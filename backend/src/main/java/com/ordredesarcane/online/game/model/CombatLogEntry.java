package com.ordredesarcane.online.game.model;

import java.time.Instant;
import java.util.UUID;

public record CombatLogEntry(
        int sequence,
        int roundNumber,
        int turnNumber,
        UUID actorPlayerId,
        String actorUsername,
        String eventType,
        String message,
        Instant timestamp
) {
}
