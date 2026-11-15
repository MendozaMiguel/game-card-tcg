package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.model.Posture;
import java.util.List;
import java.util.UUID;

public record CardViewDto(
        String instanceId,
        UUID ownerPlayerId,
        int slot,
        boolean hidden,
        boolean faceDown,
        String cardBackSkin,
        boolean foil,
        String cardId,
        String name,
        Faction faction,
        CardType cardType,
        Boolean epic,
        Boolean ramp,
        Integer energyCost,
        Integer baseAtk,
        Integer baseDef,
        Integer effectiveAtk,
        Integer effectiveDef,
        List<String> keywords,
        String description,
        List<AbilityDefinition> abilitiesScript,
        Posture posture,
        boolean exhausted,
        boolean slowed,
        boolean camouflaged,
        boolean activatedThisTurn,
        boolean changedPostureThisTurn,
        boolean canAttackNow,
        Integer curseRemainingTurns
) {
}
