package com.ordredesarcane.online.deck;

public class DeckValidationException extends RuntimeException {

    private final DeckValidationResult validationResult;

    public DeckValidationException(DeckValidationResult validationResult) {
        super("Deck invalide selon les 4 Lois Anti-Méta : " + String.join(" | ", validationResult.violations()));
        this.validationResult = validationResult;
    }

    public DeckValidationResult getValidationResult() {
        return validationResult;
    }
}
