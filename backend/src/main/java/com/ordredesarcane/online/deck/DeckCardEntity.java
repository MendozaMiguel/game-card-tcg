package com.ordredesarcane.online.deck;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "deck_cards")
public class DeckCardEntity {

    @EmbeddedId
    private DeckCardId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deckId")
    @JoinColumn(name = "deck_id", nullable = false)
    private DeckEntity deck;

    @Column(name = "is_foil", nullable = false)
    private boolean foil;

    @Column(name = "position", nullable = false)
    private int position;

    protected DeckCardEntity() {
    }

    public DeckCardEntity(DeckEntity deck, String cardId, boolean foil, int position) {
        this.deck = deck;
        this.id = new DeckCardId(deck.getDeckId(), cardId);
        this.foil = foil;
        this.position = position;
    }

    public DeckCardId getId() { return id; }
    public String getCardId() { return id.getCardId(); }
    public boolean isFoil() { return foil; }
    public int getPosition() { return position; }
}
