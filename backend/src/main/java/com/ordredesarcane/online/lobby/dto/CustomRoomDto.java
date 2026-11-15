package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.GameMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomRoomDto(
        String roomCode,
        String name,
        GameMode mode,
        UUID hostUserId,
        boolean allowSpectators,
        boolean started,
        UUID matchId,
        List<CustomRoomSlotDto> slots,
        int spectatorCount,
        Instant createdAt
) {
}
