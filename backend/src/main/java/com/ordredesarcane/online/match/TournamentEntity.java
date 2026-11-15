package com.ordredesarcane.online.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tournaments")
public class TournamentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bracket_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> bracketData = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected TournamentEntity() {
    }

    public TournamentEntity(String name, int maxPlayers, String status) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getTournamentId() { return tournamentId; }
    public String getName() { return name; }
    public int getMaxPlayers() { return maxPlayers; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getBracketData() { return bracketData; }
    public void setBracketData(Map<String, Object> bracketData) { this.bracketData = bracketData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
