package com.ordredesarcane.online.deck.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveDeckRequest(
        @NotBlank
        @Size(min = 2, max = 64)
        String name,
        @NotEmpty
        @Size(min = 30, max = 30, message = "Un deck doit contenir exactement 30 cartes")
        List<@Valid DeckCardInput> cards,
        boolean favorite
) {
}
