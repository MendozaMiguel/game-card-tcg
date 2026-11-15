package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.game.model.CombatLogEntry;
import com.ordredesarcane.online.game.model.CombatVisualEvent;
import com.ordredesarcane.online.game.model.EndReason;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.TeamId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlayerGameView(
        UUID matchId,
        GameMode mode,
        boolean ranked,
        boolean aiMatch,
        boolean customRoom,
        boolean spectator,
        UUID viewerUserId,
        UUID activeTurnPlayerId,
        List<UUID> controlledRelays,
        GamePhase phase,
        Instant phaseDeadline,
        int turnNumber,
        int roundNumber,
        boolean turn1TruceActive,
        TeamViewDto teamA,
        TeamViewDto teamB,
        List<PlayerBoardViewDto> players,
        PendingReactionViewDto pendingReaction,
        PendingChoiceViewDto pendingChoice,
        TeamId winnerTeam,
        EndReason endReason,
        List<CombatLogEntry> combatLog,
        List<CombatVisualEvent> visualEvents
) {
}
