package com.ordredesarcane.online.lobby.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
        UUID messageId,
        UUID matchId,
        UUID senderUserId,
        String senderUsername,
        boolean teamOnly,
        String content,
        Instant timestamp
) {
}
