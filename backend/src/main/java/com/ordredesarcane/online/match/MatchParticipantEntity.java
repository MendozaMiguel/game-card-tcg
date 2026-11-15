package com.ordredesarcane.online.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_participants")
public class MatchParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "is_bot", nullable = false)
    private boolean bot;

    @Column(name = "team_id", length = 16, nullable = false)
    private String teamId;

    @Column(name = "seat_index", nullable = false)
    private int seatIndex;

    @Column(name = "won", nullable = false)
    private boolean won;

    @Column(name = "primary_faction", length = 16)
    private String primaryFaction;

    @Column(name = "final_lp")
    private Integer finalLp;

    @Column(name = "mmr_delta", nullable = false)
    private int mmrDelta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MatchParticipantEntity() {
    }

    public MatchParticipantEntity(
            MatchEntity match,
            UUID userId,
            String username,
            boolean bot,
            String teamId,
            int seatIndex,
            boolean won,
            String primaryFaction,
            Integer finalLp,
            int mmrDelta,
            Instant createdAt) {
        this.match = match;
        this.userId = userId;
        this.username = username;
        this.bot = bot;
        this.teamId = teamId;
        this.seatIndex = seatIndex;
        this.won = won;
        this.primaryFaction = primaryFaction;
        this.finalLp = finalLp;
        this.mmrDelta = mmrDelta;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public MatchEntity getMatch() { return match; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isBot() { return bot; }
    public String getTeamId() { return teamId; }
    public int getSeatIndex() { return seatIndex; }
    public boolean isWon() { return won; }
    public String getPrimaryFaction() { return primaryFaction; }
    public Integer getFinalLp() { return finalLp; }
    public int getMmrDelta() { return mmrDelta; }
    public Instant getCreatedAt() { return createdAt; }
}
