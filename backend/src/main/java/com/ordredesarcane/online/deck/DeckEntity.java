package com.ordredesarcane.online.deck;

import com.ordredesarcane.online.catalog.Faction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decks")
public class DeckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_faction", length = 16, nullable = false)
    private Faction primaryFaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_faction", length = 16)
    private Faction secondaryFaction;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(name = "is_valid_antimeta", nullable = false)
    private boolean validAntimeta;

    @Column(name = "is_starter", nullable = false)
    private boolean starter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<DeckCardEntity> cards = new ArrayList<>();

    protected DeckEntity() {
    }

    public DeckEntity(
            UUID userId,
            String name,
            Faction primaryFaction,
            Faction secondaryFaction,
            boolean favorite,
            boolean validAntimeta,
            boolean starter) {
        this.userId = userId;
        this.name = name;
        this.primaryFaction = primaryFaction;
        this.secondaryFaction = secondaryFaction;
        this.favorite = favorite;
        this.validAntimeta = validAntimeta;
        this.starter = starter;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getDeckId() { return deckId; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public Faction getPrimaryFaction() { return primaryFaction; }
    public void setPrimaryFaction(Faction primaryFaction) { this.primaryFaction = primaryFaction; touch(); }
    public Faction getSecondaryFaction() { return secondaryFaction; }
    public void setSecondaryFaction(Faction secondaryFaction) { this.secondaryFaction = secondaryFaction; touch(); }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; touch(); }
    public boolean isValidAntimeta() { return validAntimeta; }
    public void setValidAntimeta(boolean validAntimeta) { this.validAntimeta = validAntimeta; touch(); }
    public boolean isStarter() { return starter; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<DeckCardEntity> getCards() { return cards; }

    public void addCard(String cardId, boolean foil, int position) {
        this.cards.add(new DeckCardEntity(this, cardId, foil, position));
    }

    public void clearCards() {
        this.cards.clear();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
