package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.catalog.AbilityOption;
import java.util.List;
import java.util.UUID;

public record PendingChoiceViewDto(
        UUID ownerPlayerId,
        String sourceInstanceId,
        String sourceCardName,
        List<AbilityOption> options,
        String preselectedTargetId
) {
}
