package com.ordredesarcane.online.lobby.dto;

import com.ordredesarcane.online.match.MatchSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchSummaryDto(
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
        List<String> teamANames,
        List<String> teamBNames,
        Integer teamALp,
        Integer teamBLp,
        String winnerTeam,
        String endReason,
        int turnCount,
        Instant createdAt,
        Instant finishedAt
) {
}
