package com.ordredesarcane.online.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "matches")
public class MatchEntity implements Persistable<UUID> {

    @Id
    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "mode_1v1_2v2", length = 8, nullable = false)
    private String mode;

    @Column(name = "is_ranked", nullable = false)
    private boolean ranked;

    @Column(name = "is_ai_match", nullable = false)
    private boolean aiMatch;

    @Column(name = "is_custom_room", nullable = false)
    private boolean customRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_source", length = 24, nullable = false)
    private MatchSource matchSource = MatchSource.CASUAL;

    @Column(name = "tournament_id")
    private UUID tournamentId;

    @Column(name = "tournament_name", length = 80)
    private String tournamentName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team_a_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> teamAIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team_b_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> teamBIds;

    @Column(name = "winner_team", length = 16)
    private String winnerTeam;

    @Column(name = "end_reason", length = 32)
    private String endReason;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "replay_log", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> replayLog = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_summary", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> matchSummary = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Transient
    private boolean isNew = true;

    protected MatchEntity() {
    }

    public MatchEntity(String mode, boolean ranked, boolean aiMatch, List<UUID> teamAIds, List<UUID> teamBIds) {
        this(
                UUID.randomUUID(),
                mode,
                ranked,
                aiMatch,
                false,
                MatchSource.resolve(ranked, aiMatch, false, null),
                null,
                null,
                teamAIds,
                teamBIds,
                Instant.now());
    }

    public MatchEntity(
            UUID matchId,
            String mode,
            boolean ranked,
            boolean aiMatch,
            boolean customRoom,
            MatchSource matchSource,
            UUID tournamentId,
            String tournamentName,
            List<UUID> teamAIds,
            List<UUID> teamBIds,
            Instant createdAt) {
        this.matchId = matchId != null ? matchId : UUID.randomUUID();
        this.mode = mode;
        this.ranked = ranked;
        this.aiMatch = aiMatch;
        this.customRoom = customRoom;
        this.matchSource = matchSource != null ? matchSource : MatchSource.resolve(ranked, aiMatch, customRoom, tournamentId);
        this.tournamentId = tournamentId;
        this.tournamentName = tournamentName;
        this.teamAIds = teamAIds;
        this.teamBIds = teamBIds;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @Override
    public UUID getId() {
        return matchId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getMatchId() { return matchId; }
    public String getMode() { return mode; }
    public boolean isRanked() { return ranked; }
    public boolean isAiMatch() { return aiMatch; }
    public boolean isCustomRoom() { return customRoom; }
    public MatchSource getMatchSource() { return matchSource; }
    public void setMatchSource(MatchSource matchSource) { this.matchSource = matchSource; }
    public UUID getTournamentId() { return tournamentId; }
    public String getTournamentName() { return tournamentName; }
    public List<UUID> getTeamAIds() { return teamAIds; }
    public List<UUID> getTeamBIds() { return teamBIds; }
    public String getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(String winnerTeam) { this.winnerTeam = winnerTeam; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
    public List<Map<String, Object>> getReplayLog() { return replayLog; }
    public void setReplayLog(List<Map<String, Object>> replayLog) { this.replayLog = replayLog; }
    public Map<String, Object> getMatchSummary() { return matchSummary; }
    public void setMatchSummary(Map<String, Object> matchSummary) {
        this.matchSummary = matchSummary != null ? matchSummary : Map.of();
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
