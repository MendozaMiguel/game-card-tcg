package com.ordredesarcane.online.deck.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ValidateDeckRequest(
        @NotNull
        @Size(max = 40)
        List<@Pattern(regexp = "^[A-Z]+_[A-Z]+_[0-9]{2}$", message = "Format cardId invalide") String> cardIds
) {
}
