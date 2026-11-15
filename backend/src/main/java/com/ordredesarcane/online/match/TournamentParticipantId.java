package com.ordredesarcane.online.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TournamentParticipantId implements Serializable {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected TournamentParticipantId() {
    }

    public TournamentParticipantId(UUID tournamentId, UUID userId) {
        this.tournamentId = tournamentId;
        this.userId = userId;
    }

    public UUID getTournamentId() { return tournamentId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TournamentParticipantId other)) return false;
        return Objects.equals(tournamentId, other.tournamentId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, userId);
    }
}
