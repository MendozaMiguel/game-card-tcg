package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.game.model.TeamId;
import java.util.List;
import java.util.UUID;

public record TeamViewDto(
        TeamId teamId,
        int lp,
        int maxLp,
        List<UUID> playerIds
) {
}
