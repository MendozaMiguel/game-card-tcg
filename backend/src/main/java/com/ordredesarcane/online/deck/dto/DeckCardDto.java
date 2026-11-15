package com.ordredesarcane.online.deck.dto;

import com.ordredesarcane.online.catalog.CardDefinition;

public record DeckCardDto(
        CardDefinition card,
        boolean foil,
        int position
) {
}
