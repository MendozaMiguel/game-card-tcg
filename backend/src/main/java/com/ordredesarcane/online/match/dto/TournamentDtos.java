package com.ordredesarcane.online.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TournamentDtos {

    private TournamentDtos() {
    }

    public record CreateTournamentRequest(
            @NotBlank @Size(min = 3, max = 80) String name,
            @Min(8) @Max(16) int maxPlayers
    ) {
    }

    public record JoinTournamentRequest(
            @NotNull UUID deck1Id,
            @NotNull UUID deck2Id
    ) {
    }

    public record TournamentParticipantDto(
            UUID userId,
            String username,
            UUID deck1Id,
            UUID deck2Id,
            int seed,
            boolean eliminated
    ) {
    }

    public record TournamentDto(
            UUID tournamentId,
            String name,
            int maxPlayers,
            String status,
            Map<String, Object> bracketData,
            List<TournamentParticipantDto> participants,
            Instant createdAt,
            Instant finishedAt
    ) {
    }
}
