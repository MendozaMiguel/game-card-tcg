package com.ordredesarcane.online.game.model;

import com.ordredesarcane.online.catalog.AbilityOption;
import java.util.List;
import java.util.UUID;

public record PendingModalChoice(
        UUID ownerPlayerId,
        String sourceInstanceId,
        String sourceCardName,
        GamePhase returnPhase,
        List<AbilityOption> options,
        String preselectedTargetId
) {
}
