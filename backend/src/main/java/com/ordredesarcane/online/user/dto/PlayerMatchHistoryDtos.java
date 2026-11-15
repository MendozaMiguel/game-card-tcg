package com.ordredesarcane.online.user.dto;

import com.ordredesarcane.online.match.MatchSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlayerMatchHistoryDtos {

    private PlayerMatchHistoryDtos() {
    }

    public record ModeStatsDto(
            MatchSource matchSource,
            String mode,
            int matches,
            int wins,
            int losses,
            double winRate
    ) {
    }

    public record PlayerStatsDto(
            int totalMatches,
            int wins,
            int losses,
            double winRate,
            int rankedWins,
            int rankedLosses,
            double rankedWinRate,
            int tournamentWins,
            int tournamentLosses,
            int aiWins,
            int aiLosses,
            int customCasualWins,
            int customCasualLosses,
            int currentWinStreak,
            double averageTurnCount,
            String favoriteFaction,
            List<String> recentForm,
            List<ModeStatsDto> breakdown
    ) {
    }

    public record MatchParticipantSummaryDto(
            UUID userId,
            String username,
            boolean bot,
            String teamId,
            int seatIndex,
            String primaryFaction
    ) {
    }

    public record PlayerMatchHistoryItemDto(
            UUID matchId,
            String mode,
            MatchSource matchSource,
            UUID tournamentId,
            String tournamentName,
            boolean won,
            String playerTeam,
            String winnerTeam,
            String endReason,
            String endSummary,
            int turnCount,
            Integer durationSeconds,
            String primaryFaction,
            int startingLp,
            Integer myTeamFinalLp,
            Integer opponentTeamFinalLp,
            int mmrDelta,
            List<MatchParticipantSummaryDto> allies,
            List<MatchParticipantSummaryDto> opponents,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    public record PlayerMatchHistoryResponse(
            PlayerStatsDto stats,
            List<PlayerMatchHistoryItemDto> matches
    ) {
    }
}
