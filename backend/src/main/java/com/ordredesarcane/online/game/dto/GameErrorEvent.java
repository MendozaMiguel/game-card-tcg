package com.ordredesarcane.online.game.dto;

import java.time.Instant;
import java.util.UUID;

public record GameErrorEvent(
        UUID matchId,
        String code,
        String message,
        Instant timestamp
) {
}
