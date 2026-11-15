package com.ordredesarcane.online.match;

import com.ordredesarcane.online.game.model.CombatLogEntry;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.user.LeagueTier;
import com.ordredesarcane.online.user.UserProfileEntity;
import com.ordredesarcane.online.user.UserProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPersistenceService {

    private static final int SHARDS_WIN = 25;
    private static final int SHARDS_LOSS = 10;
    private static final int CROWNS_WIN_RANKED = 3;
    private static final int CROWNS_WIN_CASUAL = 1;
    private static final int MMR_FLOOR = 400;
    private static final int MMR_GAIN_WIN = 24;
    private static final int MMR_LOSS = -18;

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final UserProfileRepository profileRepository;

    public MatchPersistenceService(
            MatchRepository matchRepository,
            MatchParticipantRepository participantRepository,
            UserProfileRepository profileRepository) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public void persistCompletedMatch(GameRoom room) {
        if (!room.isFinished() || room.isPersisted()) {
            return;
        }
        room.setPersisted(true);

        Instant finishedAt = room.getFinishedAt() != null ? room.getFinishedAt() : Instant.now();
        List<UUID> teamAIds = room.getTeam(TeamId.TEAM_A).getPlayerIds();
        List<UUID> teamBIds = room.getTeam(TeamId.TEAM_B).getPlayerIds();
        String mode = room.getMode() == GameMode.DUEL_1V1 ? "1V1" : "2V2";
        MatchSource source = room.getMatchSource() != null
                ? room.getMatchSource()
                : MatchSource.resolve(room.isRanked(), room.isAiMatch(), room.isCustomRoom(), room.getTournamentId());
        boolean hadRelayTakeover = room.getPlayers().values().stream()
                .anyMatch(p -> p.getControlledByUserId() != null);

        MatchEntity entity = new MatchEntity(
                room.getMatchId(),
                mode,
                room.isRanked(),
                room.isAiMatch(),
                room.isCustomRoom(),
                source,
                room.getTournamentId(),
                room.getTournamentName(),
                teamAIds,
                teamBIds,
                room.getCreatedAt());
        entity.setWinnerTeam(room.getWinnerTeam() != null ? room.getWinnerTeam().name() : null);
        entity.setEndReason(room.getEndReason() != null ? room.getEndReason().name() : null);
        entity.setTurnCount(room.getTurnNumber());
        entity.setFinishedAt(finishedAt);
        entity.setReplayLog(serializeCombatLog(room));
        entity.setMatchSummary(buildMatchSummary(room, finishedAt, hadRelayTakeover));
        MatchEntity savedMatch = matchRepository.save(entity);

        Map<UUID, String> playerFactions = new HashMap<>();
        for (PlayerState player : room.getPlayers().values()) {
            playerFactions.put(player.getUserId(), resolveMajorityFaction(player));
        }

        Map<UUID, Integer> mmrDeltas = applyProfileRewards(room, playerFactions, hadRelayTakeover);
        saveMatchParticipants(room, savedMatch, playerFactions, mmrDeltas);
    }

    private List<Map<String, Object>> serializeCombatLog(GameRoom room) {
        return room.getCombatLog().stream()
                .map(entry -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("seq", entry.sequence());
                    m.put("round", entry.roundNumber());
                    m.put("turn", entry.turnNumber());
                    m.put("actor", entry.actorUsername());
                    m.put("type", entry.eventType());
                    m.put("message", entry.message());
                    return m;
                })
                .toList();
    }

    private Map<String, Object> buildMatchSummary(GameRoom room, Instant finishedAt, boolean hadRelayTakeover) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startingLp", room.getTeam(TeamId.TEAM_A).getMaxLp());
        summary.put("teamALp", room.getTeam(TeamId.TEAM_A).getLp());
        summary.put("teamBLp", room.getTeam(TeamId.TEAM_B).getLp());
        summary.put("teamANames", room.getTeamPlayers(TeamId.TEAM_A).stream()
                .map(PlayerState::getUsername)
                .toList());
        summary.put("teamBNames", room.getTeamPlayers(TeamId.TEAM_B).stream()
                .map(PlayerState::getUsername)
                .toList());
        summary.put("durationSeconds", (int) Math.max(0L, Duration.between(room.getCreatedAt(), finishedAt).getSeconds()));
        summary.put("hadRelayTakeover", hadRelayTakeover);

        room.getCombatLog().stream()
                .filter(entry -> "MATCH_FINISHED".equals(entry.eventType()))
                .reduce((first, second) -> second)
                .map(CombatLogEntry::message)
                .ifPresent(msg -> summary.put("endSummary", msg));
        return summary;
    }

    private Map<UUID, Integer> applyProfileRewards(
            GameRoom room,
            Map<UUID, String> playerFactions,
            boolean hadRelayTakeover) {
        Map<UUID, Integer> mmrDeltas = new HashMap<>();
        if (room.isCustomRoom() || room.isAiMatch() || room.getWinnerTeam() == null) {
            return mmrDeltas;
        }

        for (PlayerState player : room.getPlayers().values()) {
            if (player.isBot()) {
                continue;
            }
            profileRepository.findById(player.getUserId()).ifPresent(profile -> {
                boolean won = player.getTeamId() == room.getWinnerTeam();
                profile.setGoldenShards(profile.getGoldenShards() + (won ? SHARDS_WIN : SHARDS_LOSS));
                if (won) {
                    profile.setArcaneCrowns(profile.getArcaneCrowns()
                            + (room.isRanked() ? CROWNS_WIN_RANKED : CROWNS_WIN_CASUAL));
                    String primaryFaction = playerFactions.getOrDefault(player.getUserId(), "FIRE");
                    Map<String, Integer> fw = new HashMap<>(profile.getFactionWins());
                    fw.merge(primaryFaction, 1, Integer::sum);
                    profile.setFactionWins(fw);
                }

                if (room.isRanked() && !hadRelayTakeover) {
                    int delta = won ? MMR_GAIN_WIN : MMR_LOSS;
                    if (room.getMode() == GameMode.DUEL_1V1) {
                        int before = profile.getMmr1v1();
                        int updated = Math.max(MMR_FLOOR, before + delta);
                        profile.setMmr1v1(updated);
                        profile.setLeagueTier(LeagueTier.fromMmr(updated));
                        mmrDeltas.put(player.getUserId(), updated - before);
                    } else {
                        int before = profile.getMmr2v2();
                        int updated = Math.max(MMR_FLOOR, before + delta);
                        profile.setMmr2v2(updated);
                        profile.setLeagueTier(LeagueTier.fromMmr(Math.max(profile.getMmr1v1(), updated)));
                        mmrDeltas.put(player.getUserId(), updated - before);
                    }
                }
                profileRepository.save(profile);
            });
        }
        return mmrDeltas;
    }

    private void saveMatchParticipants(
            GameRoom room,
            MatchEntity savedMatch,
            Map<UUID, String> playerFactions,
            Map<UUID, Integer> mmrDeltas) {
        List<MatchParticipantEntity> participants = new ArrayList<>();
        List<PlayerState> orderedPlayers = room.getPlayers().values().stream()
                .sorted(Comparator.comparingInt(PlayerState::getSeatIndex))
                .toList();
        for (PlayerState player : orderedPlayers) {
            boolean won = room.getWinnerTeam() != null && player.getTeamId() == room.getWinnerTeam();
            UUID userId = player.isBot() ? null : player.getUserId();
            int teamLp = room.getTeam(player.getTeamId()).getLp();
            int mmrDelta = player.isBot() ? 0 : mmrDeltas.getOrDefault(player.getUserId(), 0);
            participants.add(new MatchParticipantEntity(
                    savedMatch,
                    userId,
                    player.getUsername(),
                    player.isBot(),
                    player.getTeamId().name(),
                    player.getSeatIndex(),
                    won,
                    playerFactions.get(player.getUserId()),
                    teamLp,
                    mmrDelta,
                    room.getCreatedAt()));
        }
        participantRepository.saveAll(participants);
    }

    private String resolveMajorityFaction(PlayerState player) {
        Map<String, Integer> counts = new HashMap<>();
        player.getHand().forEach(c -> counts.merge(c.getDefinition().faction().name(), 1, Integer::sum));
        player.getDeck().forEach(c -> counts.merge(c.getDefinition().faction().name(), 1, Integer::sum));
        player.getDiscard().forEach(c -> counts.merge(c.getDefinition().faction().name(), 1, Integer::sum));
        player.getAllBoardCreatures().forEach(c -> counts.merge(c.getDefinition().faction().name(), 1, Integer::sum));
        player.getAllSpellArtifacts().forEach(c -> counts.merge(c.getDefinition().faction().name(), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("FIRE");
    }
}
