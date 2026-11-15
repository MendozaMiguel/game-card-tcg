package com.ordredesarcane.online.game.model;

import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.match.MatchSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class GameRoom {

    private final UUID matchId;
    private final GameMode mode;
    private final boolean ranked;
    private final boolean aiMatch;
    private final boolean customRoom;
    private final MatchSource matchSource;
    private final UUID tournamentId;
    private final String tournamentName;
    private final Instant createdAt = Instant.now();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<TeamId, TeamState> teams = new EnumMap<>(TeamId.class);
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    private final List<UUID> turnOrder = new ArrayList<>();
    private final Set<UUID> spectatorIds = ConcurrentHashMap.newKeySet();

    private GamePhase phase = GamePhase.MULLIGAN;
    private Instant phaseDeadline;
    private long savedTurnRemainingMillis = 60_000L;

    private int roundNumber = 1;
    private int turnNumber = 1;
    private int activeTurnIndex = 0;
    private boolean combatPhaseStartedThisTurn = false;

    private PendingReactionWindow pendingReaction;
    private PendingModalChoice pendingChoice;

    private TeamId winnerTeam;
    private EndReason endReason;
    private Instant finishedAt;
    private boolean persisted;

    private final AtomicInteger logSequence = new AtomicInteger(0);
    private final List<CombatLogEntry> combatLog = new ArrayList<>();
    private final AtomicInteger visualSequence = new AtomicInteger(0);
    private final List<CombatVisualEvent> visualEvents = new ArrayList<>();

    public GameRoom(
            UUID matchId,
            GameMode mode,
            boolean ranked,
            boolean aiMatch,
            boolean customRoom,
            TeamState teamA,
            TeamState teamB,
            List<PlayerState> orderedPlayers) {
        this(
                matchId,
                mode,
                ranked,
                aiMatch,
                customRoom,
                MatchSource.resolve(ranked, aiMatch, customRoom, null),
                null,
                null,
                teamA,
                teamB,
                orderedPlayers);
    }

    public GameRoom(
            UUID matchId,
            GameMode mode,
            boolean ranked,
            boolean aiMatch,
            boolean customRoom,
            MatchSource matchSource,
            UUID tournamentId,
            String tournamentName,
            TeamState teamA,
            TeamState teamB,
            List<PlayerState> orderedPlayers) {
        this.matchId = matchId;
        this.mode = mode;
        this.ranked = ranked;
        this.aiMatch = aiMatch;
        this.customRoom = customRoom;
        this.matchSource = matchSource != null ? matchSource : MatchSource.resolve(ranked, aiMatch, customRoom, tournamentId);
        this.tournamentId = tournamentId;
        this.tournamentName = tournamentName;
        this.teams.put(TeamId.TEAM_A, teamA);
        this.teams.put(TeamId.TEAM_B, teamB);
        for (PlayerState player : orderedPlayers) {
            this.players.put(player.getUserId(), player);
            this.turnOrder.add(player.getUserId());
        }
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public UUID getMatchId() { return matchId; }
    public GameMode getMode() { return mode; }
    public boolean isRanked() { return ranked; }
    public boolean isAiMatch() { return aiMatch; }
    public boolean isCustomRoom() { return customRoom; }
    public MatchSource getMatchSource() { return matchSource; }
    public UUID getTournamentId() { return tournamentId; }
    public String getTournamentName() { return tournamentName; }
    public Instant getCreatedAt() { return createdAt; }
    public Map<TeamId, TeamState> getTeams() { return teams; }
    public Map<UUID, PlayerState> getPlayers() { return players; }
    public List<UUID> getTurnOrder() { return Collections.unmodifiableList(turnOrder); }
    public Set<UUID> getSpectatorIds() { return spectatorIds; }

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public Instant getPhaseDeadline() { return phaseDeadline; }
    public void setPhaseDeadline(Instant phaseDeadline) { this.phaseDeadline = phaseDeadline; }
    public long getSavedTurnRemainingMillis() { return savedTurnRemainingMillis; }
    public void setSavedTurnRemainingMillis(long millis) { this.savedTurnRemainingMillis = Math.max(1_000L, millis); }

    public int getRoundNumber() { return roundNumber; }
    public void incrementRoundNumber() { this.roundNumber++; }
    public int getTurnNumber() { return turnNumber; }
    public void incrementTurnNumber() { this.turnNumber++; }
    public int getActiveTurnIndex() { return activeTurnIndex; }
    public void setActiveTurnIndex(int activeTurnIndex) { this.activeTurnIndex = activeTurnIndex; }

    public boolean isCombatPhaseStartedThisTurn() { return combatPhaseStartedThisTurn; }
    public void setCombatPhaseStartedThisTurn(boolean v) { this.combatPhaseStartedThisTurn = v; }

    public boolean isTurn1TruceActive() {
        return roundNumber == 1;
    }

    public PlayerState getActivePlayer() {
        return players.get(turnOrder.get(activeTurnIndex));
    }

    public PlayerState getPlayer(UUID userId) {
        return players.get(userId);
    }

    public TeamState getTeam(TeamId teamId) {
        return teams.get(teamId);
    }

    public List<PlayerState> getTeamPlayers(TeamId teamId) {
        return players.values().stream()
                .filter(p -> p.getTeamId() == teamId)
                .toList();
    }

    public List<PlayerState> getOpponentPlayers(TeamId myTeamId) {
        return getTeamPlayers(myTeamId.opponent());
    }

    public Optional<PlayerState> findTeammate(UUID playerId) {
        PlayerState player = players.get(playerId);
        if (player == null) {
            return Optional.empty();
        }
        return players.values().stream()
                .filter(p -> p.getTeamId() == player.getTeamId() && !p.getUserId().equals(playerId))
                .findFirst();
    }

    public Optional<CardInstance> findCreatureOnAnyBoard(String instanceId) {
        for (PlayerState player : players.values()) {
            Optional<CardInstance> found = player.findOnBoard(instanceId);
            if (found.isPresent() && !found.get().getDefinition().cardType().isSpellCategory()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public Optional<CardInstance> findAnyCardOnAnyBoard(String instanceId) {
        for (PlayerState player : players.values()) {
            Optional<CardInstance> found = player.findOnBoard(instanceId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public PendingReactionWindow getPendingReaction() { return pendingReaction; }
    public void setPendingReaction(PendingReactionWindow pendingReaction) { this.pendingReaction = pendingReaction; }
    public PendingModalChoice getPendingChoice() { return pendingChoice; }
    public void setPendingChoice(PendingModalChoice pendingChoice) { this.pendingChoice = pendingChoice; }

    public TeamId getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(TeamId winnerTeam) { this.winnerTeam = winnerTeam; }
    public EndReason getEndReason() { return endReason; }
    public void setEndReason(EndReason endReason) { this.endReason = endReason; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public boolean isPersisted() { return persisted; }
    public void setPersisted(boolean persisted) { this.persisted = persisted; }

    public boolean isFinished() {
        return phase == GamePhase.FINISHED;
    }

    public List<CombatLogEntry> getCombatLog() {
        return Collections.unmodifiableList(combatLog);
    }

    public List<CombatVisualEvent> getVisualEvents() {
        return Collections.unmodifiableList(visualEvents);
    }

    public void recordVisualEvent(
            Faction faction,
            UUID sourcePlayerId,
            String sourceInstanceId,
            String sourceZone,
            int sourceSlot,
            UUID targetPlayerId,
            String targetInstanceId,
            String targetZone,
            int targetSlot,
            boolean directAttack,
            Posture defenderPosture,
            CombatOutcome outcome,
            int damageDealt,
            int recoilDamage) {
        visualEvents.add(new CombatVisualEvent(
                visualSequence.incrementAndGet(),
                faction != null ? faction : Faction.FIRE,
                sourcePlayerId,
                sourceInstanceId,
                sourceZone,
                sourceSlot,
                targetPlayerId,
                targetInstanceId,
                targetZone,
                targetSlot,
                directAttack,
                defenderPosture,
                outcome,
                damageDealt,
                recoilDamage));
        while (visualEvents.size() > 32) {
            visualEvents.removeFirst();
        }
    }

    public void addLog(UUID actorPlayerId, String actorUsername, String eventType, String message) {
        combatLog.add(new CombatLogEntry(
                logSequence.incrementAndGet(),
                roundNumber,
                turnNumber,
                actorPlayerId,
                actorUsername,
                eventType,
                message,
                Instant.now()));
    }
}
