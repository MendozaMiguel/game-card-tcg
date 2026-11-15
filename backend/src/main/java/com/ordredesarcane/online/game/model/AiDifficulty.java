package com.ordredesarcane.online.game.model;

/**
 * Niveau d'intelligence tactique attribué au Mage Automate.
 */
public enum AiDifficulty {
    EASY("Faible"),
    MEDIUM("Moyen"),
    HARD("Fort");

    private final String displayLabel;

    AiDifficulty(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public static AiDifficulty resolveOrDefault(AiDifficulty difficulty) {
        return difficulty != null ? difficulty : MEDIUM;
    }
}
