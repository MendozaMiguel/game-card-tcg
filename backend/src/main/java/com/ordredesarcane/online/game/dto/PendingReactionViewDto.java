package com.ordredesarcane.online.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PendingReactionViewDto(
        UUID windowId,
        String triggerType,
        UUID currentResponderPlayerId,
        Instant expiresAt,
        UUID attackerPlayerId,
        String attackerInstanceId,
        String defenderTargetId,
        boolean directAttack,
        UUID sourceActorPlayerId,
        String sourceCardInstanceId,
        List<String> eligibleReactiveInstanceIds
) {
}
