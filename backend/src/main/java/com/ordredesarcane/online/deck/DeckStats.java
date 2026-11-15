package com.ordredesarcane.online.deck;

import com.ordredesarcane.online.catalog.Faction;
import java.util.Map;

public record DeckStats(
        int totalCards,
        int monsterCount,
        int standardMonsterCount,
        int epicMonsterCount,
        int familiarCount,
        int spellCategoryCount,
        int cost1To2Count,
        int cost3To4Count,
        int cost5PlusCount,
        int rampCount,
        Map<Faction, Integer> factionCounts,
        Faction primaryFaction,
        Faction secondaryFaction
) {
}
