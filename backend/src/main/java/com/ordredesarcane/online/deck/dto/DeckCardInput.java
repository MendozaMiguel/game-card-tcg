package com.ordredesarcane.online.deck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeckCardInput(
        @NotBlank
        @Pattern(regexp = "^[A-Z]+_[A-Z]+_[0-9]{2}$", message = "Format cardId invalide")
        String cardId,
        boolean foil
) {
}
