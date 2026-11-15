package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.EndReason;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.game.model.TeamState;
import com.ordredesarcane.online.lobby.LobbyController;
import com.ordredesarcane.online.lobby.dto.MatchSummaryDto;
import com.ordredesarcane.online.match.MatchEntity;
import com.ordredesarcane.online.match.MatchParticipantEntity;
import com.ordredesarcane.online.match.MatchParticipantRepository;
import com.ordredesarcane.online.match.MatchParticipantRepository.FactionAggregate;
import com.ordredesarcane.online.match.MatchParticipantRepository.SourceModeAggregate;
import com.ordredesarcane.online.match.MatchPersistenceService;
import com.ordredesarcane.online.match.MatchRepository;
import com.ordredesarcane.online.match.MatchSource;
import com.ordredesarcane.online.user.LeagueTier;
import com.ordredesarcane.online.user.PlayerMatchHistoryService;
import com.ordredesarcane.online.user.UserProfileEntity;
import com.ordredesarcane.online.user.UserProfileRepository;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.PlayerMatchHistoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class MatchHistoryTests {

    private CardCatalogService catalog;
    private MatchRepository matchRepository;
    private MatchParticipantRepository participantRepository;
    private UserProfileRepository profileRepository;
    private MatchPersistenceService persistenceService;
    private PlayerMatchHistoryService historyService;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        catalog = CardCatalogService.fromClasspathResource(mapper);
        matchRepository = mock(MatchRepository.class);
        participantRepository = mock(MatchParticipantRepository.class);
        profileRepository = mock(UserProfileRepository.class);
        when(matchRepository.save(any(MatchEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        persistenceService = new MatchPersistenceService(matchRepository, participantRepository, profileRepository);
        historyService = new PlayerMatchHistoryService(participantRepository, profileRepository);
    }

    private PlayerState playerWithFaction(UUID id, String name, boolean bot, TeamId team, int seat, Faction faction) {
        PlayerState ps = new PlayerState(
                id,
                name,
                bot,
                team,
                seat,
                "card_back_default",
                "board_sanctuary",
                "pet_none",
                "energy_arcane",
                "avatar_initiate",
                "frame_apprenti",
                "Apprenti Arcane",
                "finisher_arcane_burst");
        List<CardDefinition> cards = catalog.getStarterDeckCards(faction);
        for (int i = 0; i < 5 && i < cards.size(); i++) {
            ps.getHand().add(new CardInstance("C" + seat + "_" + i, id, cards.get(i), false));
        }
        return ps;
    }

    @Test
    void persistRanked1v1RecordsParticipantsSummaryAndClampedMmrDeltas() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();

        TeamState teamA = new TeamState(TeamId.TEAM_A, 25, List.of(winnerId));
        TeamState teamB = new TeamState(TeamId.TEAM_B, 25, List.of(loserId));
        teamA.applyDamage(9); // 16 LP left
        teamB.applyDamage(25); // 0 LP left

        PlayerState winner = playerWithFaction(winnerId, "Archimage", false, TeamId.TEAM_A, 0, Faction.FIRE);
        PlayerState loser = playerWithFaction(loserId, "Apprenti", false, TeamId.TEAM_B, 1, Faction.ICE);

        GameRoom room = new GameRoom(matchId, GameMode.DUEL_1V1, true, false, false, teamA, teamB, List.of(winner, loser));
        room.setWinnerTeam(TeamId.TEAM_A);
        room.setEndReason(EndReason.LP_ZERO);
        room.setPhase(GamePhase.FINISHED);
        room.setFinishedAt(room.getCreatedAt().plusSeconds(145));
        room.addLog(null, "Système", "MATCH_FINISHED", "L'Équipe B tombe à 0 PV ! Victoire de TEAM_A !");

        UserProfileEntity winnerProfile = new UserProfileEntity(winnerId);
        winnerProfile.setMmr1v1(1000);
        UserProfileEntity loserProfile = new UserProfileEntity(loserId);
        loserProfile.setMmr1v1(410); // Test clamping at MMR floor (400 => delta -10)

        when(profileRepository.findById(winnerId)).thenReturn(Optional.of(winnerProfile));
        when(profileRepository.findById(loserId)).thenReturn(Optional.of(loserProfile));

        persistenceService.persistCompletedMatch(room);

        ArgumentCaptor<MatchEntity> matchCaptor = ArgumentCaptor.forClass(MatchEntity.class);
        verify(matchRepository).save(matchCaptor.capture());
        MatchEntity savedMatch = matchCaptor.getValue();
        assertThat(savedMatch.getMatchId()).isEqualTo(matchId);
        assertThat(savedMatch.getMatchSource()).isEqualTo(MatchSource.RANKED);
        assertThat(savedMatch.getMatchSummary())
                .containsEntry("startingLp", 25)
                .containsEntry("teamALp", 16)
                .containsEntry("teamBLp", 0)
                .containsEntry("endSummary", "L'Équipe B tombe à 0 PV ! Victoire de TEAM_A !");

        assertThat(winnerProfile.getMmr1v1()).isEqualTo(1024);
        assertThat(winnerProfile.getArcaneCrowns()).isEqualTo(3);
        assertThat(winnerProfile.getGoldenShards()).isEqualTo(25);
        assertThat(winnerProfile.getFactionWins()).containsEntry("FIRE", 1);
        assertThat(winnerProfile.getLeagueTier()).isEqualTo(LeagueTier.APPRENTI);
        assertThat(loserProfile.getMmr1v1()).isEqualTo(400);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipantEntity>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(participantRepository).saveAll(partsCaptor.capture());
        List<MatchParticipantEntity> savedParts = partsCaptor.getValue();
        assertThat(savedParts).hasSize(2);
        assertThat(savedParts.get(0).getUserId()).isEqualTo(winnerId);
        assertThat(savedParts.get(0).isWon()).isTrue();
        assertThat(savedParts.get(0).getPrimaryFaction()).isEqualTo("FIRE");
        assertThat(savedParts.get(0).getFinalLp()).isEqualTo(16);
        assertThat(savedParts.get(0).getMmrDelta()).isEqualTo(24);

        assertThat(savedParts.get(1).getUserId()).isEqualTo(loserId);
        assertThat(savedParts.get(1).isWon()).isFalse();
        assertThat(savedParts.get(1).getPrimaryFaction()).isEqualTo("ICE");
        assertThat(savedParts.get(1).getFinalLp()).isEqualTo(0);
        assertThat(savedParts.get(1).getMmrDelta()).isEqualTo(-10);
    }

    @Test
    void persistAiAndTournamentMatchesStoresSourceAndNullBotUserId() {
        UUID matchId = UUID.randomUUID();
        UUID humanId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();

        TeamState teamA = new TeamState(TeamId.TEAM_A, 25, List.of(humanId));
        TeamState teamB = new TeamState(TeamId.TEAM_B, 25, List.of(botId));
        PlayerState human = playerWithFaction(humanId, "root", false, TeamId.TEAM_A, 0, Faction.SHADOW);
        PlayerState bot = playerWithFaction(botId, "Mage Automate (LIGHT)", true, TeamId.TEAM_B, 1, Faction.LIGHT);

        GameRoom aiRoom = new GameRoom(matchId, GameMode.DUEL_1V1, false, true, false, teamA, teamB, List.of(human, bot));
        aiRoom.setWinnerTeam(TeamId.TEAM_A);
        aiRoom.setEndReason(EndReason.LP_ZERO);
        aiRoom.setPhase(GamePhase.FINISHED);

        persistenceService.persistCompletedMatch(aiRoom);

        verify(profileRepository, never()).save(any());

        ArgumentCaptor<MatchEntity> matchCaptor = ArgumentCaptor.forClass(MatchEntity.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getMatchSource()).isEqualTo(MatchSource.AI);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipantEntity>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(participantRepository).saveAll(partsCaptor.capture());
        assertThat(partsCaptor.getValue()).hasSize(2);
        assertThat(partsCaptor.getValue().get(0).getUserId()).isEqualTo(humanId);
        assertThat(partsCaptor.getValue().get(0).isBot()).isFalse();
        assertThat(partsCaptor.getValue().get(1).getUserId()).isNull();
        assertThat(partsCaptor.getValue().get(1).isBot()).isTrue();
        assertThat(partsCaptor.getValue().get(1).getPrimaryFaction()).isEqualTo("LIGHT");

        UUID tourneyId = UUID.randomUUID();
        UUID tMatchId = UUID.randomUUID();
        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();
        TeamState tA = new TeamState(TeamId.TEAM_A, 25, List.of(p1Id));
        TeamState tB = new TeamState(TeamId.TEAM_B, 25, List.of(p2Id));
        GameRoom tourneyRoom = new GameRoom(
                tMatchId,
                GameMode.DUEL_1V1,
                false,
                false,
                false,
                MatchSource.TOURNAMENT,
                tourneyId,
                "Coupe d'Hiver",
                tA,
                tB,
                List.of(
                        playerWithFaction(p1Id, "P1", false, TeamId.TEAM_A, 0, Faction.FIRE),
                        playerWithFaction(p2Id, "P2", false, TeamId.TEAM_B, 1, Faction.ICE)));
        tourneyRoom.setWinnerTeam(TeamId.TEAM_B);
        tourneyRoom.setEndReason(EndReason.FORFEIT);
        tourneyRoom.setPhase(GamePhase.FINISHED);

        persistenceService.persistCompletedMatch(tourneyRoom);
        verify(matchRepository, org.mockito.Mockito.times(2)).save(matchCaptor.capture());
        MatchEntity savedTourney = matchCaptor.getValue();
        assertThat(savedTourney.getMatchSource()).isEqualTo(MatchSource.TOURNAMENT);
        assertThat(savedTourney.getTournamentId()).isEqualTo(tourneyId);
        assertThat(savedTourney.getTournamentName()).isEqualTo("Coupe d'Hiver");
    }

    @Test
    void playerHistoryServiceComputesStatsWinRateStreakAndOverview() {
        UUID userId = UUID.randomUUID();
        UUID allyId = UUID.randomUUID();
        UUID oppId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        SourceModeAggregate ranked1v1 = new SourceModeAggregate() {
            @Override public MatchSource getMatchSource() { return MatchSource.RANKED; }
            @Override public String getMode() { return "1V1"; }
            @Override public long getTotalCount() { return 4; }
            @Override public long getWinCount() { return 3; }
            @Override public Long getTurnSum() { return 40L; }
        };
        SourceModeAggregate tourney1v1 = new SourceModeAggregate() {
            @Override public MatchSource getMatchSource() { return MatchSource.TOURNAMENT; }
            @Override public String getMode() { return "1V1"; }
            @Override public long getTotalCount() { return 2; }
            @Override public long getWinCount() { return 1; }
            @Override public Long getTurnSum() { return 18L; }
        };

        FactionAggregate favShadow = new FactionAggregate() {
            @Override public String getFaction() { return "SHADOW"; }
            @Override public long getPlayCount() { return 4; }
            @Override public long getWinCount() { return 3; }
        };

        when(participantRepository.summarizeBySourceAndMode(userId)).thenReturn(List.of(ranked1v1, tourney1v1));
        when(participantRepository.summarizeFactionsByUserId(userId)).thenReturn(List.of(favShadow));
        when(participantRepository.findRecentOutcomesByUserId(eq(userId), any()))
                .thenReturn(List.of(true, true, false, true, true, false));

        MatchEntity match = new MatchEntity(
                matchId,
                "2V2",
                true,
                false,
                false,
                MatchSource.RANKED,
                null,
                null,
                List.of(userId, allyId),
                List.of(oppId, UUID.randomUUID()),
                Instant.parse("2026-11-15T01:00:00Z"));
        match.setWinnerTeam("TEAM_A");
        match.setEndReason("LP_ZERO");
        match.setTurnCount(11);
        match.setFinishedAt(Instant.parse("2026-11-15T01:03:20Z"));
        match.setMatchSummary(Map.of(
                "startingLp", 40,
                "teamALp", 22,
                "teamBLp", 0,
                "endSummary", "L'Équipe B tombe à 0 PV ! Victoire de TEAM_A !"));

        MatchParticipantEntity self = new MatchParticipantEntity(
                match, userId, "MageMoi", false, "TEAM_A", 0, true, "SHADOW", 22, 24, match.getCreatedAt());
        MatchParticipantEntity ally = new MatchParticipantEntity(
                match, allyId, "AlliéClair", false, "TEAM_A", 2, true, "LIGHT", 22, 24, match.getCreatedAt());
        MatchParticipantEntity opp1 = new MatchParticipantEntity(
                match, oppId, "RivalGlace", false, "TEAM_B", 1, false, "ICE", 0, -18, match.getCreatedAt());
        MatchParticipantEntity opp2 = new MatchParticipantEntity(
                match, null, "Mage Automate", true, "TEAM_B", 3, false, "FIRE", 0, 0, match.getCreatedAt());

        when(participantRepository.findRecentWithMatchByUserId(userId, PageRequest.of(0, 20)))
                .thenReturn(List.of(self));
        when(participantRepository.findAllForMatchesOrdered(anyCollection()))
                .thenReturn(List.of(self, opp1, ally, opp2));

        PlayerMatchHistoryResponse response = historyService.getPlayerHistory(userId, 20, null);

        assertThat(response.stats().totalMatches()).isEqualTo(6);
        assertThat(response.stats().wins()).isEqualTo(4);
        assertThat(response.stats().losses()).isEqualTo(2);
        assertThat(response.stats().winRate()).isEqualTo(66.7);
        assertThat(response.stats().rankedWinRate()).isEqualTo(75.0);
        assertThat(response.stats().tournamentWins()).isEqualTo(1);
        assertThat(response.stats().tournamentLosses()).isEqualTo(1);
        assertThat(response.stats().currentWinStreak()).isEqualTo(2);
        assertThat(response.stats().averageTurnCount()).isEqualTo(9.7);
        assertThat(response.stats().favoriteFaction()).isEqualTo("SHADOW");
        assertThat(response.stats().recentForm()).containsExactly("W", "W", "L", "W", "W", "L");

        assertThat(response.matches()).hasSize(1);
        var item = response.matches().getFirst();
        assertThat(item.won()).isTrue();
        assertThat(item.matchSource()).isEqualTo(MatchSource.RANKED);
        assertThat(item.myTeamFinalLp()).isEqualTo(22);
        assertThat(item.opponentTeamFinalLp()).isEqualTo(0);
        assertThat(item.startingLp()).isEqualTo(40);
        assertThat(item.durationSeconds()).isEqualTo(200);
        assertThat(item.mmrDelta()).isEqualTo(24);
        assertThat(item.allies()).extracting(a -> a.username()).containsExactly("AlliéClair");
        assertThat(item.opponents()).extracting(o -> o.username()).containsExactly("RivalGlace", "Mage Automate");
    }

    @Test
    void lobbySummaryDtoHandlesBothEnrichedAndLegacyMatches() {
        UUID mId = UUID.randomUUID();
        MatchEntity m = new MatchEntity(
                mId,
                "1V1",
                false,
                false,
                true,
                MatchSource.CUSTOM,
                null,
                null,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                Instant.now());
        m.setWinnerTeam("TEAM_A");
        m.setEndReason("FORFEIT");
        m.setTurnCount(8);
        m.setMatchSummary(Map.of(
                "teamANames", List.of("root"),
                "teamBNames", List.of("root2"),
                "teamALp", 19,
                "teamBLp", 12));

        MatchSummaryDto dto = LobbyController.toMatchSummaryDto(m);
        assertThat(dto.matchSource()).isEqualTo(MatchSource.CUSTOM);
        assertThat(dto.customRoom()).isTrue();
        assertThat(dto.teamANames()).containsExactly("root");
        assertThat(dto.teamBNames()).containsExactly("root2");
        assertThat(dto.teamALp()).isEqualTo(19);
        assertThat(dto.teamBLp()).isEqualTo(12);
    }
}
