package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.dto.CardViewDto;
import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.dto.PlayerBoardViewDto;
import com.ordredesarcane.online.game.dto.PlayerGameView;
import com.ordredesarcane.online.game.engine.AbilityInterpreter;
import com.ordredesarcane.online.game.engine.BoardRuleEvaluator;
import com.ordredesarcane.online.game.engine.CombatResolver;
import com.ordredesarcane.online.game.engine.FogOfWarProjector;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.engine.MageAutomateBot;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.CombatOutcome;
import com.ordredesarcane.online.game.model.CombatVisualEvent;
import com.ordredesarcane.online.game.model.EndReason;
import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.match.MatchPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class GameEngineTests {

    private CardCatalogService catalog;
    private BoardRuleEvaluator evaluator;
    private FogOfWarProjector projector;
    private MageAutomateBot bot;
    private GameEngineService engine;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        catalog = CardCatalogService.fromClasspathResource(mapper);
        evaluator = new BoardRuleEvaluator();
        AbilityInterpreter abilities = new AbilityInterpreter(evaluator);
        CombatResolver combatResolver = new CombatResolver(evaluator, abilities);
        projector = new FogOfWarProjector(evaluator, catalog);
        bot = new MageAutomateBot(evaluator, abilities, combatResolver, projector);
        engine = new GameEngineService(
                evaluator,
                abilities,
                combatResolver,
                projector,
                bot,
                mock(MatchPersistenceService.class),
                mock(SimpMessagingTemplate.class));
        engine.setAsyncBotEnabled(false);
    }

    private CardDefinition card(String id) {
        return catalog.findById(id).orElseThrow();
    }

    private List<CardDefinition> customDeck(String... frontCardIds) {
        List<CardDefinition> list = new ArrayList<>();
        for (String id : frontCardIds) {
            list.add(card(id));
        }
        List<CardDefinition> filler = catalog.getStarterDeckCards(Faction.FIRE);
        int idx = 0;
        while (list.size() < 30) {
            list.add(filler.get(idx % filler.size()));
            idx++;
        }
        return list;
    }

    private void completeMulliganForAll(GameRoom room) {
        for (UUID pid : room.getTurnOrder()) {
            PlayerState ps = room.getPlayer(pid);
            if (!ps.isMulliganCompleted()) {
                engine.processAction(pid, cmd(room.getMatchId(), GameActionType.MULLIGAN_SWAP, null, null, null, null, List.of()));
            }
        }
    }

    private GameActionCommand cmd(
            UUID matchId,
            GameActionType type,
            String cardInstanceId,
            Integer slot,
            Posture posture,
            String targetId,
            List<String> instanceIds) {
        return new GameActionCommand(matchId, null, type, cardInstanceId, slot, posture, targetId, null, null, instanceIds);
    }

    private void endTurnHandlingDiscard(GameRoom room, UUID playerId) {
        engine.processAction(playerId, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        if (room.getPhase() == GamePhase.WAITING_FOR_DISCARD) {
            PlayerState ps = room.getPlayer(playerId);
            int extra = ps.getHand().size() - PlayerState.MAX_HAND_SIZE_END_TURN;
            List<String> ids = ps.getHand().subList(ps.getHand().size() - extra, ps.getHand().size())
                    .stream().map(c -> c.getInstanceId()).toList();
            engine.processAction(playerId, cmd(room.getMatchId(), GameActionType.DISCARD_EXCESS, null, null, null, null, ids));
        }
    }

    @Test
    void duel1v1Turn1TruceBlocksAttackAndAllowsInRound2() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> deck1 = customDeck("FIRE_M_01", "FIRE_M_03", "FIRE_S_01", "FIRE_S_01", "FIRE_M_01", "FIRE_M_02");
        List<CardDefinition> deck2 = customDeck("ICE_M_01", "ICE_M_02", "ICE_M_03", "ICE_M_04", "ICE_M_05", "ICE_F_01");

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "MageA", false, TeamId.TEAM_A, deck1),
                MatchParticipantSpec.simple(p2, "MageB", false, TeamId.TEAM_B, deck2)));
        assertThat(room.getTeam(TeamId.TEAM_A).getLp()).isEqualTo(25);
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(25);
        assertThat(room.getPhase()).isEqualTo(GamePhase.MULLIGAN);

        completeMulliganForAll(room);
        assertThat(room.getPhase()).isEqualTo(GamePhase.MAIN_PHASE);
        assertThat(room.isTurn1TruceActive()).isTrue();
        assertThat(room.getPlayer(p1).getEnergyTokens()).isEqualTo(1);
        assertThat(room.getPlayer(p1).getHand()).hasSize(6);

        String salId = room.getPlayer(p1).getHand().getFirst().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, salId, 0, Posture.ATTACK, null, null));

        assertThatThrownBy(() -> engine.processAction(
                p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, salId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trêve du Tour 1");

        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        assertThat(room.isTurn1TruceActive()).isTrue();

        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        assertThat(room.getRoundNumber()).isEqualTo(2);
        assertThat(room.isTurn1TruceActive()).isFalse();

        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, salId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(23); // 1 atk + 1 ON_ATTACK_SURVIVE Salamandre
    }

    @Test
    void combatMatrixAtqVsAtqAndAtqVsDefAndZeroDefDestructionAndEpicCap() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> d1 = customDeck(
                "LIGHT_M_06", "FIRE_M_08", "FIRE_M_09", "LIGHT_S_03", "FIRE_M_03",
                "FIRE_M_03", "FIRE_M_03", "FIRE_M_03", "FIRE_M_03", "FIRE_M_03");
        List<CardDefinition> d2 = customDeck(
                "FIRE_M_01", "LIGHT_M_02", "ICE_M_09", "LIGHT_M_01", "ICE_M_01",
                "ICE_M_01", "ICE_M_01", "ICE_M_01", "ICE_M_01", "ICE_M_01");

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "Attaquant", false, TeamId.TEAM_A, d1),
                MatchParticipantSpec.simple(p2, "Defenseur", false, TeamId.TEAM_B, d2)));
        completeMulliganForAll(room);

        // Advance through Turn 1 -> Round 2 (verifying WAITING_FOR_DISCARD on P1's 7th card)
        endTurnHandlingDiscard(room, p1);
        endTurnHandlingDiscard(room, p2);
        assertThat(room.getPlayer(p1).getHand()).hasSize(7);
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.WAITING_FOR_DISCARD);
        String extraId = room.getPlayer(p1).getHand().getLast().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DISCARD_EXCESS, null, null, null, null, List.of(extraId)));
        endTurnHandlingDiscard(room, p2);
        endTurnHandlingDiscard(room, p1);
        endTurnHandlingDiscard(room, p2);

        room.getLock().lock();
        try {
            room.getPlayer(p1).addEnergy(15);
            room.getPlayer(p2).addEnergy(15);
        } finally {
            room.getLock().unlock();
        }

        String epic1 = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_08")).findFirst().orElseThrow().getInstanceId();
        String epic2 = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_09")).findFirst().orElseThrow().getInstanceId();
        String p1Spell = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_S_03")).findFirst().orElseThrow().getInstanceId();
        String p1Monster = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_M_06")).findFirst().orElseThrow().getInstanceId();

        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, epic1, 0, Posture.ATTACK, null, null));
        assertThatThrownBy(() -> engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, epic2, 1, Posture.ATTACK, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Épique");

        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, p1Monster, 1, Posture.ATTACK, null, null));
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        if (room.getPhase() == GamePhase.WAITING_FOR_DISCARD) {
            int extra = room.getPlayer(p1).getHand().size() - 6;
            List<String> ids = room.getPlayer(p1).getHand().stream().limit(extra).map(c -> c.getInstanceId()).toList();
            engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DISCARD_EXCESS, null, null, null, null, ids));
        }

        // P2 summons Gardien (LIGHT_M_02 2/3) in DEFENSE & Sal (FIRE_M_01 1/1) in ATTACK & Golem de Glace (ICE_M_09 3/6) in DEFENSE
        String gardienId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_M_02")).findFirst().orElseThrow().getInstanceId();
        String p2SalId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_01")).findFirst().orElseThrow().getInstanceId();
        String iceGolemId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_M_09")).findFirst().orElseThrow().getInstanceId();

        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, gardienId, 0, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, p2SalId, 1, Posture.ATTACK, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, iceGolemId, 2, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        if (room.getPhase() == GamePhase.WAITING_FOR_DISCARD) {
            int extra = room.getPlayer(p2).getHand().size() - 6;
            List<String> ids = room.getPlayer(p2).getHand().stream().limit(extra).map(c -> c.getInstanceId()).toList();
            engine.processAction(p2, cmd(room.getMatchId(), GameActionType.DISCARD_EXCESS, null, null, null, null, ids));
        }

        // P1 attacks non-Gardien blocked!
        assertThatThrownBy(() -> engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, epic1, null, null, p2SalId, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gardien");

        // P1 Phénix (4 ATK) vs Gardien (3 DEF in DEFENSE): Gardien destroyed, 0 LP damage!
        int p2LpBefore = room.getTeam(TeamId.TEAM_B).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, epic1, null, null, gardienId, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(p2LpBefore);
        assertThat(room.findCreatureOnAnyBoard(gardienId)).isEmpty();

        // Now Paladin (3 ATK) attacks Salamandre (1 ATK in ATTACK): Salamandre destroyed + (3-1=2) LP excess!
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, p1Monster, null, null, p2SalId, null));
        assertThat(room.findCreatureOnAnyBoard(p2SalId)).isEmpty();
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(p2LpBefore - 2);
    }

    @Test
    void spellReduction0DefAndIceGolemDefenseImmunityAndReactiveCardsAndCharge() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> d1 = customDeck("FIRE_M_05", "LIGHT_S_03", "FIRE_M_01", "SHADOW_F_01", "ICE_M_03", "FIRE_M_02");
        List<CardDefinition> d2 = customDeck("ICE_F_07", "FIRE_S_05", "ICE_M_09", "LIGHT_M_01", "ICE_S_03", "FIRE_M_01");

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "Hero", false, TeamId.TEAM_A, d1),
                MatchParticipantSpec.simple(p2, "Counter", false, TeamId.TEAM_B, d2)));
        completeMulliganForAll(room);

        room.getLock().lock();
        try {
            room.getPlayer(p1).addEnergy(20);
            room.getPlayer(p2).addEnergy(20);
        } finally {
            room.getLock().unlock();
        }

        // Advance to Round 2 P2 so P2 can set Tempête de Cendres + summon Loutre Arctique + Licorne + Golem
        endTurnHandlingDiscard(room, p1);
        endTurnHandlingDiscard(room, p2);
        endTurnHandlingDiscard(room, p1);

        String otterId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_F_07")).findFirst().orElseThrow().getInstanceId();
        String ashStormId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_S_05")).findFirst().orElseThrow().getInstanceId();
        String golemId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_M_09")).findFirst().orElseThrow().getInstanceId();
        String unicornId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_M_01")).findFirst().orElseThrow().getInstanceId();
        String gelId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_S_03")).findFirst().orElseThrow().getInstanceId();

        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, otterId, 0, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, unicornId, 0, Posture.ATTACK, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, golemId, 1, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.SET_REACTIVE_SPELL, ashStormId, 0, null, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.SET_REACTIVE_SPELL, gelId, 1, null, null, null));

        // Fog of War check before P2 ends turn: P1 must NOT see Ash Storm cardId!
        PlayerGameView p1ViewBefore = projector.projectForPlayer(room, p1, false);
        PlayerBoardViewDto p2AsSeenByP1 = p1ViewBefore.players().get(1);
        assertThat(p2AsSeenByP1.hand()).allSatisfy(c -> {
            assertThat(c.hidden()).isTrue();
            assertThat(c.cardId()).isNull();
        });
        assertThat(p2AsSeenByP1.spellArtifacts()).hasSize(2).allSatisfy(c -> {
            assertThat(c.hidden()).isTrue();
            assertThat(c.faceDown()).isTrue();
            assertThat(c.cardId()).isNull();
        });

        endTurnHandlingDiscard(room, p2);

        // P1 plays Rayon de Lumière (-2 DEF) on Licorne (2/2) -> drops to 0 DEF -> destroyed!
        String rayId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_S_03")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, rayId, null, null, unicornId, null));
        assertThat(room.findCreatureOnAnyBoard(unicornId)).isEmpty();

        // P1 plays Bélier de Magma (CHARGE 3/3) and Salamandre (1/1)
        String ramId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_05")).findFirst().orElseThrow().getInstanceId();
        String salId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_01")).findFirst().orElseThrow().getInstanceId();
        String famId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_F_01")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, ramId, 0, Posture.ATTACK, null, null));
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, salId, 1, Posture.ATTACK, null, null));
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, famId, 0, Posture.ATTACK, null, null));

        // Gel Arcanique opens reaction when P1 activates Mini-Spectre!
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.ACTIVATE_EFFECT, famId, null, null, null, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.WAITING_FOR_REACTION);
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.TRIGGER_REACTION, gelId, null, null, null, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.MAIN_PHASE);
        assertThat(room.findCreatureOnAnyBoard(famId).orElseThrow().isExhausted()).isTrue();

        // CHARGE attack (Bélier) against Golem (3/6): reaction window is BYPASSED despite Ash Storm & Otter!
        int p1LpBeforeRecoil = room.getTeam(TeamId.TEAM_A).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, ramId, null, null, golemId, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.COMBAT_PHASE);
        assertThat(room.getTeam(TeamId.TEAM_A).getLp()).isEqualTo(p1LpBeforeRecoil - 3); // 6 DEF - 3 ATK = 3 recoil

        // Normal attack (Salamandre) against Golem: opens 12s Reaction Window -> P2 uses Tempête de Cendres!
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, salId, null, null, golemId, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.WAITING_FOR_REACTION);
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.TRIGGER_REACTION, ashStormId, null, null, null, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.COMBAT_PHASE);
        // No recoil LP damage taken by P1 because Tempête de Cendres cancelled the attack!
        assertThat(room.getTeam(TeamId.TEAM_A).getLp()).isEqualTo(p1LpBeforeRecoil - 3);
    }

    @Test
    void mode2v2Shared40LpAlternatingTurnsAndRelaisArcane() {
        UUID a1 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        List<CardDefinition> deck = customDeck("FIRE_M_01", "FIRE_F_01", "FIRE_M_03", "FIRE_M_04", "FIRE_S_01");

        GameRoom room = engine.createMatch(GameMode.TEAM_2V2, true, false, false, false, List.of(
                MatchParticipantSpec.simple(a1, "Alpha1", false, TeamId.TEAM_A, deck),
                MatchParticipantSpec.simple(a2, "Alpha2", false, TeamId.TEAM_A, deck),
                MatchParticipantSpec.simple(b1, "Beta1", false, TeamId.TEAM_B, deck),
                MatchParticipantSpec.simple(b2, "Beta2", false, TeamId.TEAM_B, deck)));
        completeMulliganForAll(room);

        assertThat(room.getTeam(TeamId.TEAM_A).getLp()).isEqualTo(40);
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(40);
        assertThat(room.getTurnOrder()).containsExactly(a1, b1, a2, b2);

        // Before relay: A1 cannot act for A2 nor see A2's hand!
        PlayerGameView beforeRelay = projector.projectForPlayer(room, a1, false);
        CardViewDto a2FirstHandCard = beforeRelay.players().get(2).hand().getFirst();
        assertThat(a2FirstHandCard.hidden()).isTrue();
        assertThat(a2FirstHandCard.cardId()).isNull();

        // Advance to A2 turn
        engine.processAction(a1, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        engine.processAction(b1, cmd(room.getMatchId(), GameActionType.END_TURN, null, null, null, null, null));
        assertThat(room.getActivePlayer().getUserId()).isEqualTo(a2);

        // Unauthorized attempt by A1 to play for A2 fails
        assertThatThrownBy(() -> engine.processAction(a1, new GameActionCommand(
                room.getMatchId(), a2, GameActionType.END_TURN, null, null, null, null, null, null, null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        // Simulate Relais d'Arcane (A2 disconnected, A1 controls A2)
        room.getLock().lock();
        try {
            room.getPlayer(a2).setConnected(false);
            room.getPlayer(a2).setControlledByUserId(a1);
        } finally {
            room.getLock().unlock();
        }

        PlayerGameView afterRelay = projector.projectForPlayer(room, a1, false);
        assertThat(afterRelay.controlledRelays()).containsExactly(a2);
        assertThat(afterRelay.players().get(2).hand().getFirst().hidden()).isFalse();
        assertThat(afterRelay.players().get(2).hand().getFirst().cardId()).isEqualTo("FIRE_M_01");

        // Now A1 can play a card AND end A2's turn via actingAsPlayerId = a2
        String a2Card = afterRelay.players().get(2).hand().getFirst().instanceId();
        engine.processAction(a1, new GameActionCommand(
                room.getMatchId(), a2, GameActionType.PLAY_CARD, a2Card, 0, Posture.ATTACK, null, null, null, null));
        engine.processAction(a1, new GameActionCommand(
                room.getMatchId(), a2, GameActionType.END_TURN, null, null, null, null, null, null, null));
        assertThat(room.getActivePlayer().getUserId()).isEqualTo(b2);

        // Spectator view keeps ALL hands and face-down spells hidden
        PlayerGameView specView = projector.projectForPlayer(room, UUID.randomUUID(), true);
        assertThat(specView.spectator()).isTrue();
        for (PlayerBoardViewDto board : specView.players()) {
            assertThat(board.hand()).allSatisfy(c -> assertThat(c.hidden()).isTrue());
        }
    }

    @Test
    void mageAutomateBotsPlayMultipleTurnsWithoutErrors() {
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, true, List.of(
                MatchParticipantSpec.simple(b1, "BotFeu", true, TeamId.TEAM_A, catalog.getStarterDeckCards(Faction.FIRE)),
                MatchParticipantSpec.simple(b2, "BotGlace", true, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.ICE))));

        engine.runBotLoopSynchronouslyForTest(room.getMatchId());
        assertThat(room.getTurnNumber()).isGreaterThan(1);
        assertThat(room.getCombatLog()).isNotEmpty();
    }

    @Test
    void deckOutWinsImmediatelyAndStrictJacksonRejectsTamperedCommand() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> smallDeck = catalog.getStarterDeckCards(Faction.SHADOW).subList(0, 5); // exactly 5 -> 0 left in deck after deal!
        List<CardDefinition> deck2 = catalog.getStarterDeckCards(Faction.LIGHT);

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "EmptyDeck", false, TeamId.TEAM_A, smallDeck),
                MatchParticipantSpec.simple(p2, "Winner", false, TeamId.TEAM_B, deck2)));
        completeMulliganForAll(room);

        // P1's Turn 1 draw fails immediately -> P2 wins by DECK_OUT!
        assertThat(room.isFinished()).isTrue();
        assertThat(room.getWinnerTeam()).isEqualTo(TeamId.TEAM_B);
        assertThat(room.getEndReason()).isEqualTo(EndReason.DECK_OUT);

        JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        String cheatPayload = """
                {"matchId":"%s","actionType":"PLAY_CARD","cheatDamage":99}
                """.formatted(UUID.randomUUID());
        assertThatThrownBy(() -> strict.readValue(cheatPayload, GameActionCommand.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void survolCamouflageRalentitCurseExpirationAndSpellLockWorkAsSpecified() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> d1 = customDeck("FIRE_M_06", "ICE_M_05", "SHADOW_S_10", "LIGHT_S_08", "LIGHT_S_08", "FIRE_M_01");
        List<CardDefinition> d2 = customDeck("FIRE_F_02", "ICE_M_09", "ICE_F_07", "SHADOW_M_01", "FIRE_S_02", "ICE_M_01");

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "Mage1", false, TeamId.TEAM_A, d1),
                MatchParticipantSpec.simple(p2, "Mage2", false, TeamId.TEAM_B, d2)));
        completeMulliganForAll(room);

        room.getLock().lock();
        try {
            room.getPlayer(p1).addEnergy(25);
            room.getPlayer(p2).addEnergy(25);
        } finally {
            room.getLock().unlock();
        }

        endTurnHandlingDiscard(room, p1);
        // P2 Turn 1: summon Lutin des Brumes (CAMOUFLAGE), Golem de Glace (DEFENSE), Loutre Arctique (REACTIVE), Loup Fantôme (ATTACK)
        String lutinId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_F_02")).findFirst().orElseThrow().getInstanceId();
        String iceGolemId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_M_09")).findFirst().orElseThrow().getInstanceId();
        String otterId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_F_07")).findFirst().orElseThrow().getInstanceId();
        String wolfId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_M_01")).findFirst().orElseThrow().getInstanceId();

        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, lutinId, 0, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.ACTIVATE_EFFECT, lutinId, null, null, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, iceGolemId, 0, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, otterId, 1, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, wolfId, 1, Posture.ATTACK, null, null));
        endTurnHandlingDiscard(room, p2);

        // Round 2 P1 Turn:
        // 1) Golem de Glace in DEFENSE survives Lame Céleste (-3 DEF x2 = -6 DEF by Spell immunity in Defense!)
        String blade1 = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_S_08")).findFirst().orElseThrow().getInstanceId();
        String blade2 = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("LIGHT_S_08")).skip(1).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, new GameActionCommand(room.getMatchId(), null, GameActionType.PLAY_CARD, blade1, null, null, iceGolemId, null, 1, null));
        engine.processAction(p1, new GameActionCommand(room.getMatchId(), null, GameActionType.PLAY_CARD, blade2, null, null, iceGolemId, null, 1, null));
        assertThat(room.findCreatureOnAnyBoard(iceGolemId)).isPresent();
        assertThat(evaluator.evaluateEffectiveDef(room, room.findCreatureOnAnyBoard(iceGolemId).orElseThrow())).isGreaterThanOrEqualTo(1);

        // 2) Spectre de Gel slows Loup Fantôme -> Loup cannot attack on P2's upcoming turn!
        String spectreId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("ICE_M_05")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, spectreId, 0, Posture.ATTACK, wolfId, null));
        assertThat(room.findCreatureOnAnyBoard(wolfId).orElseThrow().isSlowed()).isTrue();

        // 3) Cannot target Camouflaged Lutin des Brumes
        String griffonId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_06")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, griffonId, 1, Posture.ATTACK, null, null));
        assertThatThrownBy(() -> engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, griffonId, null, null, lutinId, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Camouflage");

        // 4) Nuit Éternelle locks opponent's Spells on their next turn!
        String nightId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_S_10")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, nightId, null, null, null, null));

        // 5) Reactive Familiar Loutre Arctique redirects Spectre attack onto itself and becomes exhausted!
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, spectreId, null, null, wolfId, null));
        assertThat(room.getPhase()).isEqualTo(GamePhase.WAITING_FOR_REACTION);
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.TRIGGER_REACTION, otterId, null, null, null, null));
        assertThat(room.findCreatureOnAnyBoard(wolfId)).isPresent();
        assertThat(room.findCreatureOnAnyBoard(otterId)).isEmpty(); // 4 ATK vs (2+1=3 DEF) destroyed Otter!

        endTurnHandlingDiscard(room, p1);

        // Round 2 P2 Turn: Loup is slowed -> cannot attack; Spells locked by Nuit Éternelle -> cannot cast Feu de Camp!
        assertThatThrownBy(() -> engine.processAction(p2, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, wolfId, null, null, spectreId, null)))
                .isInstanceOf(IllegalArgumentException.class);
        String campFireId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_S_02")).findFirst().orElseThrow().getInstanceId();
        assertThatThrownBy(() -> engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, campFireId, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verrouillés");

        // Clean board for SURVOL test: sacrifice P2's Golem (switch Wolf? wait destroy P2 monsters -> give P1 blade if needed, or simpler verify SURVOL works directly when only Familiar remains!)
        room.getLock().lock();
        try {
            room.getPlayer(p2).removeFromBoard(iceGolemId);
            room.getPlayer(p2).removeFromBoard(wolfId);
            // Put one non-camouflaged Familiar (ICE_F_02) on P2 board; P2 still has 1 Familiar, 0 Monsters!
            CardDefinition fDef = card("ICE_F_02");
            var fInst = new com.ordredesarcane.online.game.model.CardInstance("EXTRA_F", p2, fDef, false);
            fInst.setSlot(2);
            room.getPlayer(p2).getFamiliars()[2] = fInst;
        } finally {
            room.getLock().unlock();
        }
        endTurnHandlingDiscard(room, p2);

        // Round 3 P1 Turn: Griffon (SURVOL) CAN direct attack despite P2's non-camouflaged Familiar, whereas Fire Salamander (non-SURVOL) CANNOT!
        String fireSalId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("FIRE_M_01")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, fireSalId, 2, Posture.ATTACK, null, null));
        assertThatThrownBy(() -> engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, fireSalId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rempart");

        int lpBeforeSurvol = room.getTeam(TeamId.TEAM_B).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, griffonId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(lpBeforeSurvol - 3);
    }

    @Test
    void expiredCamouflageStopsHidingKeywordAndSurvolBypassesFamiliarsButNotMonsters() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<CardDefinition> d1 = customDeck("SHADOW_M_03", "SHADOW_F_04", "FIRE_M_01", "FIRE_M_02", "ICE_M_01", "LIGHT_M_01");
        List<CardDefinition> d2 = customDeck("SHADOW_M_02", "SHADOW_F_02", "FIRE_F_01", "ICE_F_02", "LIGHT_F_01", "SHADOW_F_01");

        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "SurvolUser", false, TeamId.TEAM_A, d1),
                MatchParticipantSpec.simple(p2, "StealthUser", false, TeamId.TEAM_B, d2)));
        completeMulliganForAll(room);

        room.getLock().lock();
        try {
            room.getPlayer(p1).addEnergy(25);
            room.getPlayer(p2).addEnergy(25);
        } finally {
            room.getLock().unlock();
        }

        String harpieId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_M_03")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, harpieId, 0, Posture.ATTACK, null, null));
        endTurnHandlingDiscard(room, p1);

        // P2 Round 1: plays Ombre Rampante (MONSTER, ON_PLAY camouflage) & Lutin Sombre (FAMILIAR, activated camouflage)
        String ombreId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_M_02")).findFirst().orElseThrow().getInstanceId();
        String lutinId = room.getPlayer(p2).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_F_02")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, ombreId, 0, Posture.ATTACK, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.PLAY_CARD, lutinId, 0, Posture.DEFENSE, null, null));
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.ACTIVATE_EFFECT, lutinId, null, null, null, null));

        CardInstance ombre = room.findCreatureOnAnyBoard(ombreId).orElseThrow();
        CardInstance lutin = room.findCreatureOnAnyBoard(lutinId).orElseThrow();
        assertThat(ombre.isCamouflaged()).isTrue();
        assertThat(lutin.isCamouflaged()).isTrue();
        assertThat(evaluator.hasKeyword(room, ombre, "CAMOUFLAGE")).isTrue();
        assertThat(evaluator.evaluateKeywords(room, ombre)).contains("CAMOUFLAGE");
        endTurnHandlingDiscard(room, p2);

        // Round 2 P1: Ombre Rampante & Lutin Sombre are BOTH actively camouflaged -> Harpie (SURVOL) CAN attack LP directly
        int lpRound2 = room.getTeam(TeamId.TEAM_B).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, harpieId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(lpRound2 - 3);
        endTurnHandlingDiscard(room, p1);

        // Round 2 P2: onOwnerTurnStart clears camouflage -> neither Ombre Rampante nor un-reactivated Lutin Sombre stays CAMOUFLAGED
        assertThat(ombre.isCamouflaged()).isFalse();
        assertThat(lutin.isCamouflaged()).isFalse();
        assertThat(evaluator.hasKeyword(room, ombre, "CAMOUFLAGE")).isFalse();
        assertThat(evaluator.evaluateKeywords(room, ombre)).doesNotContain("CAMOUFLAGE");
        assertThat(evaluator.hasKeyword(room, lutin, "CAMOUFLAGE")).isFalse();
        assertThat(evaluator.evaluateKeywords(room, lutin)).doesNotContain("CAMOUFLAGE");
        // Re-activate Lutin Sombre only -> Familiar has CAMOUFLAGE, Monster Ombre Rampante does NOT
        engine.processAction(p2, cmd(room.getMatchId(), GameActionType.ACTIVATE_EFFECT, lutinId, null, null, null, null));
        assertThat(evaluator.hasKeyword(room, lutin, "CAMOUFLAGE")).isTrue();
        endTurnHandlingDiscard(room, p2);

        // Round 3 P1: Summon Mini-Harpie (SURVOL), verify Harpie CANNOT direct-attack past exposed Monster Ombre Rampante, CAN destroy it, then Mini-Harpie (SURVOL) bypasses camouflaged Familiar Lutin Sombre
        String miniHarpieId = room.getPlayer(p1).getHand().stream().filter(c -> c.getDefinition().cardId().equals("SHADOW_F_04")).findFirst().orElseThrow().getInstanceId();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.PLAY_CARD, miniHarpieId, 0, Posture.ATTACK, null, null));

        assertThatThrownBy(() -> engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, harpieId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monstre adverse");

        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, harpieId, null, null, ombreId, null));
        assertThat(room.findCreatureOnAnyBoard(ombreId)).isEmpty();

        int lpRound3AfterClear = room.getTeam(TeamId.TEAM_B).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, miniHarpieId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(lpRound3AfterClear - 1);

        endTurnHandlingDiscard(room, p1);
        endTurnHandlingDiscard(room, p2); // Lutin's stealth expired too, leaving only un-camouflaged Familiar

        int lpRound4 = room.getTeam(TeamId.TEAM_B).getLp();
        engine.processAction(p1, cmd(room.getMatchId(), GameActionType.DECLARE_ATTACK, harpieId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null));
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(lpRound4 - 3);
    }

    @Test
    void instantMulliganReplaceSwapsCardInPlaceOncePerSlotBeforeConfirming() {
        UUID p1 = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(p1, "MulliganMage", false, TeamId.TEAM_A, catalog.getStarterDeckCards(Faction.FIRE)),
                MatchParticipantSpec.simple(bot, "Automate", true, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.ICE))));

        assertThat(room.getPhase()).isEqualTo(GamePhase.MULLIGAN);
        PlayerBoardViewDto initialOwnerBoard = projector.projectForPlayer(room, p1, false).players().stream()
                .filter(p -> p.userId().equals(p1)).findFirst().orElseThrow();
        assertThat(initialOwnerBoard.hand()).hasSize(5);
        assertThat(initialOwnerBoard.mulliganReplacedSlots()).isEmpty();

        String originalSlot1Id = initialOwnerBoard.hand().get(1).instanceId();
        String slot0IdBefore = initialOwnerBoard.hand().get(0).instanceId();

        PlayerGameView afterReplace = engine.processAction(
                p1,
                cmd(room.getMatchId(), GameActionType.MULLIGAN_REPLACE_CARD, originalSlot1Id, null, null, null, null));

        assertThat(afterReplace.phase()).isEqualTo(GamePhase.MULLIGAN);
        PlayerBoardViewDto updatedOwnerBoard = afterReplace.players().stream()
                .filter(p -> p.userId().equals(p1)).findFirst().orElseThrow();
        assertThat(updatedOwnerBoard.mulliganCompleted()).isFalse();
        assertThat(updatedOwnerBoard.mulliganReplacedSlots()).containsExactly(1);
        assertThat(updatedOwnerBoard.hand()).hasSize(5);
        assertThat(updatedOwnerBoard.hand().get(0).instanceId()).isEqualTo(slot0IdBefore);
        String newSlot1Id = updatedOwnerBoard.hand().get(1).instanceId();
        assertThat(newSlot1Id).isNotEqualTo(originalSlot1Id);

        // Fog of War: opponent/spectator must NOT see private mulliganReplacedSlots
        PlayerBoardViewDto spectatedP1 = projector.projectForPlayer(room, bot, true).players().stream()
                .filter(p -> p.userId().equals(p1)).findFirst().orElseThrow();
        assertThat(spectatedP1.mulliganReplacedSlots()).isEmpty();

        // Anti-exploit: cannot replace the exact same slot twice during Mulligan
        assertThatThrownBy(() -> engine.processAction(
                p1,
                cmd(room.getMatchId(), GameActionType.MULLIGAN_REPLACE_CARD, newSlot1Id, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà été remplacé");

        // Confirm Mulligan enters Tour 1 (MAIN_PHASE) immediately against bot
        PlayerGameView afterConfirm = engine.processAction(
                p1,
                cmd(room.getMatchId(), GameActionType.MULLIGAN_SWAP, null, null, null, null, List.of()));
        assertThat(afterConfirm.phase()).isEqualTo(GamePhase.MAIN_PHASE);
    }

    @Test
    void mageAutomateBotsAtAllDifficultyTiersPlayAndExposeDifficultyInView() {
        UUID bEasy = UUID.randomUUID();
        UUID bHard = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, true, List.of(
                MatchParticipantSpec.simple(bEasy, "BotFaible", true, TeamId.TEAM_A, catalog.getStarterDeckCards(Faction.LIGHT), AiDifficulty.EASY),
                MatchParticipantSpec.simple(bHard, "BotFort", true, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.SHADOW), AiDifficulty.HARD)));

        PlayerGameView view = projector.projectForPlayer(room, bEasy, false);
        assertThat(view.players())
                .extracting(PlayerBoardViewDto::username, PlayerBoardViewDto::aiDifficulty)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("BotFaible", AiDifficulty.EASY),
                        org.assertj.core.groups.Tuple.tuple("BotFort", AiDifficulty.HARD));

        engine.runBotLoopSynchronouslyForTest(room.getMatchId());
        assertThat(room.getTurnNumber()).isGreaterThan(1);
        assertThat(room.getCombatLog()).isNotEmpty();
    }

    @Test
    void mageAutomateDifficultyShapesMulliganDiscardModalAndCombatTactics() {
        UUID easyBotId = UUID.randomUUID();
        UUID medBotId = UUID.randomUUID();
        UUID hardBotId = UUID.randomUUID();
        UUID humanId = UUID.randomUUID();

        // Check Mulligan & Discard behavior directly across difficulty tiers
        GameRoom easyRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(easyBotId, "BotFaible", true, TeamId.TEAM_A, customDeck("ICE_M_09", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.EASY),
                MatchParticipantSpec.simple(humanId, "Joueur", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));
        GameRoom medRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(medBotId, "BotMoyen", true, TeamId.TEAM_A, customDeck("ICE_M_09", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.MEDIUM),
                MatchParticipantSpec.simple(UUID.randomUUID(), "Joueur2", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));
        GameRoom hardRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(hardBotId, "BotFort", true, TeamId.TEAM_A, customDeck("ICE_M_09", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(UUID.randomUUID(), "Joueur3", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));

        GameActionCommand easyMulligan = bot.chooseMulliganAction(easyRoom, easyRoom.getPlayer(easyBotId));
        GameActionCommand medMulligan = bot.chooseMulliganAction(medRoom, medRoom.getPlayer(medBotId));
        GameActionCommand hardMulligan = bot.chooseMulliganAction(hardRoom, hardRoom.getPlayer(hardBotId));
        assertThat(easyMulligan.instanceIds()).isEmpty();
        assertThat(medMulligan.instanceIds()).hasSize(1);
        assertThat(hardMulligan.instanceIds()).hasSize(1);

        // Add 3 extra cheap cards to force 8-card hand excess discard
        for (int i = 0; i < 3; i++) {
            easyRoom.getPlayer(easyBotId).getHand().add(new CardInstance("ED" + i, easyBotId, card("FIRE_M_01"), false));
            medRoom.getPlayer(medBotId).getHand().add(new CardInstance("MD" + i, medBotId, card("FIRE_M_01"), false));
        }
        GameActionCommand easyDiscard = bot.chooseDiscardAction(easyRoom, easyRoom.getPlayer(easyBotId));
        GameActionCommand medDiscard = bot.chooseDiscardAction(medRoom, medRoom.getPlayer(medBotId));
        String easyDiscardedId = easyDiscard.instanceIds().getFirst();
        String medDiscardedId = medDiscard.instanceIds().getFirst();
        int easyDiscardedCost = easyRoom.getPlayer(easyBotId).findInHand(easyDiscardedId).orElseThrow().getDefinition().energyCost();
        int medDiscardedCost = medRoom.getPlayer(medBotId).findInHand(medDiscardedId).orElseThrow().getDefinition().energyCost();
        assertThat(easyDiscardedCost).isEqualTo(1);
        assertThat(medDiscardedCost).isEqualTo(5);

        // Tactical comparison at lethal LP (enemy at 2 LP with a Monster on board + FIRE_S_03 in hand)
        UUID tBotMed = UUID.randomUUID();
        UUID tBotHard = UUID.randomUUID();
        UUID tOppMed = UUID.randomUUID();
        UUID tOppHard = UUID.randomUUID();
        GameRoom tacticalMedRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(tBotMed, "BotMoyen", true, TeamId.TEAM_A, customDeck("FIRE_S_03", "LIGHT_S_04", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.MEDIUM),
                MatchParticipantSpec.simple(tOppMed, "Opp", false, TeamId.TEAM_B, customDeck("FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"))));
        GameRoom tacticalHardRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(tBotHard, "BotFort", true, TeamId.TEAM_A, customDeck("FIRE_S_03", "LIGHT_S_04", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(tOppHard, "Opp", false, TeamId.TEAM_B, customDeck("FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"))));

        for (GameRoom r : List.of(tacticalMedRoom, tacticalHardRoom)) {
            completeMulliganForAll(r);
            r.getTeam(TeamId.TEAM_B).applyDamage(23); // Leave opponent at 2 LP
            UUID botPid = r == tacticalMedRoom ? tBotMed : tBotHard;
            UUID oppPid = r == tacticalMedRoom ? tOppMed : tOppHard;
            r.getPlayer(botPid).addEnergy(5);
            CardInstance oppMon = new CardInstance("OPP_MON_" + r.getMatchId(), oppPid, card("FIRE_M_01"), false);
            oppMon.setSlot(0);
            oppMon.setPosture(Posture.ATTACK);
            r.getPlayer(oppPid).getMonsters()[0] = oppMon;
        }

        GameActionCommand medPlay = bot.chooseNextTurnAction(tacticalMedRoom, tacticalMedRoom.getPlayer(tBotMed));
        GameActionCommand hardPlay = bot.chooseNextTurnAction(tacticalHardRoom, tacticalHardRoom.getPlayer(tBotHard));
        assertThat(medPlay.choiceIndex()).isEqualTo(0); // MEDIUM defaults to removal first
        assertThat(hardPlay.choiceIndex()).isEqualTo(1); // HARD takes the immediate 2 LP lethal burn

        // EASY AI does not set reactives at high LP and does not proactively flip DEFENSE -> ATTACK
        UUID tBotEasy = UUID.randomUUID();
        UUID tOppEasy = UUID.randomUUID();
        GameRoom easyTacticRoom = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(tBotEasy, "BotFaible", true, TeamId.TEAM_A, customDeck("LIGHT_S_04", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.EASY),
                MatchParticipantSpec.simple(tOppEasy, "Opp", false, TeamId.TEAM_B, customDeck("FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"))));
        completeMulliganForAll(easyTacticRoom);
        PlayerState easyPs = easyTacticRoom.getPlayer(tBotEasy);
        easyPs.getHand().removeIf(c -> c.getDefinition().cardType() != com.ordredesarcane.online.catalog.CardType.SPELL_REACTIVE);
        easyPs.addEnergy(5);
        CardInstance defMonster = new CardInstance("EASY_DEF", tBotEasy, card("FIRE_M_06"), false);
        defMonster.setSlot(0);
        defMonster.setPosture(Posture.DEFENSE);
        easyPs.getMonsters()[0] = defMonster;
        easyTacticRoom.incrementRoundNumber(); // Past Turn 1 Truce

        GameActionCommand easyAction = bot.chooseNextTurnAction(easyTacticRoom, easyPs);
        assertThat(easyAction.actionType()).isEqualTo(GameActionType.END_TURN);
    }

    @Test
    void combatVisualEventsExposeDeckFactionProjectilesAndDefenseTankOutcomes() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, false, false, false, List.of(
                MatchParticipantSpec.simple(p1, "MagePyro", false, TeamId.TEAM_A, catalog.getStarterDeckCards(Faction.FIRE)),
                MatchParticipantSpec.simple(p2, "MageCryo", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.ICE))));

        PlayerGameView initialView = projector.projectForPlayer(room, p1, false);
        assertThat(initialView.players())
                .extracting(PlayerBoardViewDto::username, PlayerBoardViewDto::primaryFaction)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("MagePyro", Faction.FIRE),
                        org.assertj.core.groups.Tuple.tuple("MageCryo", Faction.ICE));

        completeMulliganForAll(room);
        room.getLock().lock();
        try {
            room.incrementRoundNumber(); // Bypass Turn 1 Truce cleanly
            PlayerState ps1 = room.getPlayer(p1);
            PlayerState ps2 = room.getPlayer(p2);

            CardInstance attackerLow = new CardInstance("ATK_LOW", p1, card("FIRE_M_01"), false);
            attackerLow.setSlot(0);
            attackerLow.setPosture(Posture.ATTACK);
            ps1.getMonsters()[0] = attackerLow;

            CardInstance defenderTank = new CardInstance("DEF_TANK", p2, card("ICE_M_09"), false);
            defenderTank.setSlot(1);
            defenderTank.setPosture(Posture.DEFENSE);
            ps2.getMonsters()[1] = defenderTank;

            CombatResolver cr = new CombatResolver(new BoardRuleEvaluator(), new AbilityInterpreter(new BoardRuleEvaluator()));
            cr.resolveCombat(room, ps1, attackerLow, "DEF_TANK", false);

            CombatVisualEvent blockEvent = room.getVisualEvents().getFirst();
            assertThat(blockEvent.faction()).isEqualTo(Faction.FIRE);
            assertThat(blockEvent.sourceZone()).isEqualTo("monster");
            assertThat(blockEvent.sourceSlot()).isEqualTo(0);
            assertThat(blockEvent.targetZone()).isEqualTo("monster");
            assertThat(blockEvent.targetSlot()).isEqualTo(1);
            assertThat(blockEvent.defenderPosture()).isEqualTo(Posture.DEFENSE);
            assertThat(blockEvent.outcome()).isEqualTo(CombatOutcome.DEFENSE_BLOCKED);
            assertThat(blockEvent.recoilDamage()).isGreaterThan(0);

            CombatVisualEvent burnTriggerEvent = room.getVisualEvents().get(1);
            assertThat(burnTriggerEvent.faction()).isEqualTo(Faction.FIRE);
            assertThat(burnTriggerEvent.directAttack()).isTrue();
            assertThat(burnTriggerEvent.outcome()).isEqualTo(CombatOutcome.SPELL_STRIKE);

            CardInstance attackerHeavy = new CardInstance("ATK_HEAVY", p1, card("FIRE_M_09"), false);
            attackerHeavy.setSlot(2);
            attackerHeavy.setPosture(Posture.ATTACK);
            ps1.getMonsters()[2] = attackerHeavy;

            // Swap tank to low-DEF creature so atkA > defD breaks it and keeps pre-destruction slot info
            CardInstance weakDef = new CardInstance("DEF_WEAK", p2, card("ICE_M_01"), false);
            weakDef.setSlot(3);
            weakDef.setPosture(Posture.DEFENSE);
            ps2.getMonsters()[3] = weakDef;

            cr.resolveCombat(room, ps1, attackerHeavy, "DEF_WEAK", false);
            CombatVisualEvent brokenEvent = room.getVisualEvents().stream()
                    .filter(ev -> "ATK_HEAVY".equals(ev.sourceInstanceId()))
                    .reduce((first, second) -> second)
                    .orElseThrow();
            assertThat(brokenEvent.faction()).isEqualTo(Faction.FIRE);
            assertThat(brokenEvent.targetZone()).isEqualTo("monster");
            assertThat(brokenEvent.targetSlot()).isEqualTo(3);
            assertThat(brokenEvent.defenderPosture()).isEqualTo(Posture.DEFENSE);
            assertThat(brokenEvent.outcome()).isEqualTo(CombatOutcome.DEFENSE_BROKEN);

            cr.resolveCombat(room, ps1, attackerHeavy, CombatResolver.DIRECT_PLAYER_TARGET, true);
            CombatVisualEvent directEvent = room.getVisualEvents().stream()
                    .filter(CombatVisualEvent::directAttack)
                    .reduce((first, second) -> second)
                    .orElseThrow();
            assertThat(directEvent.directAttack()).isTrue();
            assertThat(directEvent.faction()).isEqualTo(Faction.FIRE);
            assertThat(directEvent.targetPlayerId()).isEqualTo(p2);
            assertThat(directEvent.outcome()).isEqualTo(CombatOutcome.DIRECT_HIT);
            assertThat(directEvent.damageDealt()).isGreaterThan(0);
        } finally {
            room.getLock().unlock();
        }
    }
}
