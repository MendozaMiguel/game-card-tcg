package com.ordredesarcane.online.catalog;

import java.util.List;

public record CardDefinition(
        String cardId,
        String name,
        Faction faction,
        CardType cardType,
        boolean isEpic,
        boolean isRamp,
        int energyCost,
        Integer baseAtk,
        Integer baseDef,
        List<String> keywords,
        String description,
        List<AbilityDefinition> abilitiesScript
) {
    public boolean requiresFactionLoyalty() {
        return isEpic || cardType == CardType.SPELL_ULTIMATE;
    }
}
