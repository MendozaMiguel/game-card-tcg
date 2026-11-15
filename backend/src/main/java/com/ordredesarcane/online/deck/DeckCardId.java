package com.ordredesarcane.online.deck;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DeckCardId implements Serializable {

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "card_id", length = 32, nullable = false)
    private String cardId;

    protected DeckCardId() {
    }

    public DeckCardId(UUID deckId, String cardId) {
        this.deckId = deckId;
        this.cardId = cardId;
    }

    public UUID getDeckId() { return deckId; }
    public String getCardId() { return cardId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeckCardId other)) return false;
        return Objects.equals(deckId, other.deckId) && Objects.equals(cardId, other.cardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deckId, cardId);
    }
}
