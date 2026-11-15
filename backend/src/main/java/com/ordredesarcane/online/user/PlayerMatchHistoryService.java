package com.ordredesarcane.online.user;

import com.ordredesarcane.online.match.MatchEntity;
import com.ordredesarcane.online.match.MatchParticipantEntity;
import com.ordredesarcane.online.match.MatchParticipantRepository;
import com.ordredesarcane.online.match.MatchParticipantRepository.FactionAggregate;
import com.ordredesarcane.online.match.MatchParticipantRepository.SourceModeAggregate;
import com.ordredesarcane.online.match.MatchSource;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.MatchParticipantSummaryDto;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.ModeStatsDto;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.PlayerMatchHistoryItemDto;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.PlayerMatchHistoryResponse;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.PlayerStatsDto;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerMatchHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int OUTCOME_SAMPLE_LIMIT = 50;
    private static final int RECENT_FORM_LIMIT = 10;

    private final MatchParticipantRepository participantRepository;
    private final UserProfileRepository profileRepository;

    public PlayerMatchHistoryService(
            MatchParticipantRepository participantRepository,
            UserProfileRepository profileRepository) {
        this.participantRepository = participantRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public PlayerMatchHistoryResponse getPlayerHistory(UUID userId, int limit, MatchSource filterSource) {
        int clampedLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        PlayerStatsDto stats = computeStats(userId);
        List<PlayerMatchHistoryItemDto> items = loadRecentItems(userId, clampedLimit, filterSource);
        return new PlayerMatchHistoryResponse(stats, items);
    }

    private PlayerStatsDto computeStats(UUID userId) {
        List<SourceModeAggregate> aggregates = participantRepository.summarizeBySourceAndMode(userId);
        List<ModeStatsDto> breakdown = new ArrayList<>();
        int totalMatches = 0;
        int totalWins = 0;
        double turnSum = 0.0;
        int rankedWins = 0;
        int rankedLosses = 0;
        int tournamentWins = 0;
        int tournamentLosses = 0;
        int aiWins = 0;
        int aiLosses = 0;
        int customCasualWins = 0;
        int customCasualLosses = 0;

        for (SourceModeAggregate agg : aggregates) {
            int count = (int) agg.getTotalCount();
            int wins = (int) agg.getWinCount();
            int losses = Math.max(0, count - wins);
            MatchSource source = agg.getMatchSource() != null ? agg.getMatchSource() : MatchSource.CASUAL;
            String mode = agg.getMode() != null ? agg.getMode() : "1V1";

            totalMatches += count;
            totalWins += wins;
            turnSum += agg.getTurnSum() != null ? agg.getTurnSum() : 0.0;

            switch (source) {
                case RANKED -> {
                    rankedWins += wins;
                    rankedLosses += losses;
                }
                case TOURNAMENT -> {
                    tournamentWins += wins;
                    tournamentLosses += losses;
                }
                case AI -> {
                    aiWins += wins;
                    aiLosses += losses;
                }
                case CASUAL, CUSTOM -> {
                    customCasualWins += wins;
                    customCasualLosses += losses;
                }
            }
            breakdown.add(new ModeStatsDto(source, mode, count, wins, losses, computeWinRate(wins, count)));
        }

        breakdown.sort(Comparator
                .comparingInt((ModeStatsDto m) -> m.matchSource().ordinal())
                .thenComparing(ModeStatsDto::mode));

        List<Boolean> recentOutcomes = participantRepository.findRecentOutcomesByUserId(
                userId, PageRequest.of(0, OUTCOME_SAMPLE_LIMIT));
        int currentWinStreak = 0;
        for (Boolean won : recentOutcomes) {
            if (Boolean.TRUE.equals(won)) {
                currentWinStreak++;
            } else {
                break;
            }
        }
        List<String> recentForm = recentOutcomes.stream()
                .limit(RECENT_FORM_LIMIT)
                .map(won -> Boolean.TRUE.equals(won) ? "W" : "L")
                .toList();

        String favoriteFaction = resolveFavoriteFaction(userId);
        int totalLosses = Math.max(0, totalMatches - totalWins);
        double averageTurns = totalMatches <= 0 ? 0.0 : Math.round((turnSum * 10.0) / totalMatches) / 10.0;

        return new PlayerStatsDto(
                totalMatches,
                totalWins,
                totalLosses,
                computeWinRate(totalWins, totalMatches),
                rankedWins,
                rankedLosses,
                computeWinRate(rankedWins, rankedWins + rankedLosses),
                tournamentWins,
                tournamentLosses,
                aiWins,
                aiLosses,
                customCasualWins,
                customCasualLosses,
                currentWinStreak,
                averageTurns,
                favoriteFaction,
                recentForm,
                breakdown);
    }

    private String resolveFavoriteFaction(UUID userId) {
        List<FactionAggregate> factions = participantRepository.summarizeFactionsByUserId(userId);
        if (!factions.isEmpty()) {
            return factions.getFirst().getFaction();
        }
        return profileRepository.findById(userId)
                .map(UserProfileEntity::getFactionWins)
                .flatMap(fw -> fw.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() > 0)
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey))
                .orElse(null);
    }

    private List<PlayerMatchHistoryItemDto> loadRecentItems(UUID userId, int limit, MatchSource filterSource) {
        PageRequest pageable = PageRequest.of(0, limit);
        List<MatchParticipantEntity> playerEntries = filterSource == null
                ? participantRepository.findRecentWithMatchByUserId(userId, pageable)
                : participantRepository.findRecentWithMatchByUserIdAndSource(userId, filterSource, pageable);

        if (playerEntries.isEmpty()) {
            return List.of();
        }

        List<UUID> matchIds = playerEntries.stream()
                .map(p -> p.getMatch().getMatchId())
                .distinct()
                .toList();

        Map<UUID, List<MatchParticipantEntity>> byMatchId = new LinkedHashMap<>();
        for (MatchParticipantEntity p : participantRepository.findAllForMatchesOrdered(matchIds)) {
            byMatchId.computeIfAbsent(p.getMatch().getMatchId(), k -> new ArrayList<>()).add(p);
        }

        List<PlayerMatchHistoryItemDto> result = new ArrayList<>(playerEntries.size());
        for (MatchParticipantEntity self : playerEntries) {
            MatchEntity match = self.getMatch();
            List<MatchParticipantEntity> allParticipants = byMatchId.getOrDefault(match.getMatchId(), List.of());
            result.add(toHistoryItem(self, match, allParticipants));
        }
        return result;
    }

    private PlayerMatchHistoryItemDto toHistoryItem(
            MatchParticipantEntity self,
            MatchEntity match,
            List<MatchParticipantEntity> allParticipants) {
        Map<String, Object> summary = match.getMatchSummary() != null ? match.getMatchSummary() : Map.of();
        MatchSource source = match.getMatchSource() != null
                ? match.getMatchSource()
                : MatchSource.resolve(match.isRanked(), match.isAiMatch(), match.isCustomRoom(), match.getTournamentId());

        String playerTeam = self.getTeamId();
        boolean teamA = "TEAM_A".equals(playerTeam);

        List<MatchParticipantSummaryDto> allies = new ArrayList<>();
        List<MatchParticipantSummaryDto> opponents = new ArrayList<>();
        Integer oppParticipantLp = null;

        for (MatchParticipantEntity p : allParticipants) {
            if (p == self || (p.getId() != null && p.getId().equals(self.getId()))) {
                continue;
            }
            MatchParticipantSummaryDto participantDto = new MatchParticipantSummaryDto(
                    p.getUserId(),
                    p.getUsername(),
                    p.isBot(),
                    p.getTeamId(),
                    p.getSeatIndex(),
                    p.getPrimaryFaction());
            if (p.getTeamId().equals(playerTeam)) {
                allies.add(participantDto);
            } else {
                opponents.add(participantDto);
                if (oppParticipantLp == null && p.getFinalLp() != null) {
                    oppParticipantLp = p.getFinalLp();
                }
            }
        }

        Integer startingLp = readInt(summary.get("startingLp"));
        if (startingLp == null) {
            startingLp = "2V2".equals(match.getMode()) ? 40 : 25;
        }
        Integer myFinalLp = self.getFinalLp() != null
                ? self.getFinalLp()
                : readInt(summary.get(teamA ? "teamALp" : "teamBLp"));
        Integer oppFinalLp = oppParticipantLp != null
                ? oppParticipantLp
                : readInt(summary.get(teamA ? "teamBLp" : "teamALp"));

        Integer durationSeconds = readInt(summary.get("durationSeconds"));
        if (durationSeconds == null && match.getCreatedAt() != null && match.getFinishedAt() != null) {
            durationSeconds = (int) Math.max(0L, Duration.between(match.getCreatedAt(), match.getFinishedAt()).getSeconds());
        }
        String endSummary = summary.get("endSummary") instanceof String s ? s : null;

        return new PlayerMatchHistoryItemDto(
                match.getMatchId(),
                match.getMode(),
                source,
                match.getTournamentId(),
                match.getTournamentName(),
                self.isWon(),
                playerTeam,
                match.getWinnerTeam(),
                match.getEndReason(),
                endSummary,
                match.getTurnCount(),
                durationSeconds,
                self.getPrimaryFaction(),
                startingLp,
                myFinalLp,
                oppFinalLp,
                self.getMmrDelta(),
                allies,
                opponents,
                match.getCreatedAt(),
                match.getFinishedAt());
    }

    private static double computeWinRate(int wins, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((wins * 1000.0) / total) / 10.0;
    }

    private static Integer readInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
