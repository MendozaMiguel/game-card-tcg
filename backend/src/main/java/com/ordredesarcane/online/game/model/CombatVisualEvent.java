package com.ordredesarcane.online.game.model;

import com.ordredesarcane.online.catalog.Faction;
import java.util.UUID;

public record CombatVisualEvent(
        int sequence,
        Faction faction,
        UUID sourcePlayerId,
        String sourceInstanceId,
        String sourceZone,
        int sourceSlot,
        UUID targetPlayerId,
        String targetInstanceId,
        String targetZone,
        int targetSlot,
        boolean directAttack,
        Posture defenderPosture,
        CombatOutcome outcome,
        int damageDealt,
        int recoilDamage
) {
}
