package com.ordredesarcane.online.deck.dto;

import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.deck.DeckValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeckDto(
        UUID deckId,
        String name,
        Faction primaryFaction,
        Faction secondaryFaction,
        boolean favorite,
        boolean validAntimeta,
        boolean starter,
        Instant updatedAt,
        List<DeckCardDto> cards,
        DeckValidationResult validation
) {
}
