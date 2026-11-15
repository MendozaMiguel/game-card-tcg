package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.game.model.TeamId;
import java.util.UUID;

public record CustomRoomSlotDto(
        UUID userId,
        String username,
        TeamId teamId,
        boolean host
) {
}
