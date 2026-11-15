package com.ordredesarcane.online.match;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tournament_participants")
public class TournamentParticipantEntity {

    @EmbeddedId
    private TournamentParticipantId id;

    @Column(name = "deck_1_id", nullable = false)
    private UUID deck1Id;

    @Column(name = "deck_2_id", nullable = false)
    private UUID deck2Id;

    @Column(name = "seed", nullable = false)
    private int seed;

    @Column(name = "eliminated", nullable = false)
    private boolean eliminated;

    protected TournamentParticipantEntity() {
    }

    public TournamentParticipantEntity(UUID tournamentId, UUID userId, UUID deck1Id, UUID deck2Id, int seed) {
        this.id = new TournamentParticipantId(tournamentId, userId);
        this.deck1Id = deck1Id;
        this.deck2Id = deck2Id;
        this.seed = seed;
        this.eliminated = false;
    }

    public TournamentParticipantId getId() { return id; }
    public UUID getDeck1Id() { return deck1Id; }
    public UUID getDeck2Id() { return deck2Id; }
    public int getSeed() { return seed; }
    public boolean isEliminated() { return eliminated; }
    public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }
}
