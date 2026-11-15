package com.ordredesarcane.online.deck;

import java.util.List;

public record DeckValidationResult(
        boolean valid,
        List<String> violations,
        DeckStats stats
) {
}
