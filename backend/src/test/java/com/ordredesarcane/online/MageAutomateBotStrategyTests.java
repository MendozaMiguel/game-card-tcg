package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.engine.AbilityInterpreter;
import com.ordredesarcane.online.game.engine.BoardRuleEvaluator;
import com.ordredesarcane.online.game.engine.CombatResolver;
import com.ordredesarcane.online.game.engine.FogOfWarProjector;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.engine.MageAutomateBot;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.match.MatchPersistenceService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class MageAutomateBotStrategyTests {

    private CardCatalogService catalog;
    private BoardRuleEvaluator evaluator;
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
        FogOfWarProjector projector = new FogOfWarProjector(evaluator, catalog);
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
        List<CardDefinition> filler = catalog.getStarterDeckCards(Faction.LIGHT);
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
                engine.processAction(pid, new GameActionCommand(
                        room.getMatchId(), null, GameActionType.MULLIGAN_SWAP,
                        null, null, null, null, null, null, List.of()));
            }
        }
    }

    @Test
    void hardBotExecutesHealThenAstralFairyEnergyChainToUnlockEpic() {
        UUID botId = UUID.randomUUID();
        UUID oppId = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botId, "HardBot", true, TeamId.TEAM_A,
                        customDeck("LIGHT_M_09", "LIGHT_M_09", "LIGHT_M_09", "LIGHT_M_09", "LIGHT_M_09"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppId, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.FIRE))));
        completeMulliganForAll(room);

        PlayerState botPs = room.getPlayer(botId);
        // Set up Licorne Lumineuse (heals LP) + Fée Astrale (grants +1⚡ if healed this turn) with 4 energy -> unlocks 5-cost LIGHT_M_09!
        botPs.addEnergy(3); // 1 -> 4 energy
        CardInstance unicorn = new CardInstance("UNI_1", botId, card("LIGHT_M_01"), false);
        unicorn.setSlot(0);
        unicorn.setPosture(Posture.ATTACK);
        botPs.getMonsters()[0] = unicorn;

        CardInstance fairy = new CardInstance("FAIRY_1", botId, card("LIGHT_F_04"), false);
        fairy.setSlot(0);
        fairy.setPosture(Posture.ATTACK);
        botPs.getFamiliars()[0] = fairy;

        // Step 1: Must activate Licorne Lumineuse FIRST (sets healedThisTurn=true)
        GameActionCommand step1 = bot.chooseNextTurnAction(room, botPs);
        assertThat(step1.actionType()).isEqualTo(GameActionType.ACTIVATE_EFFECT);
        assertThat(step1.cardInstanceId()).isEqualTo("UNI_1");
        engine.processAction(botId, step1);

        // Step 2: Now healedThisTurn=true -> MUST activate Fée Astrale (+1⚡ -> total 5⚡)
        GameActionCommand step2 = bot.chooseNextTurnAction(room, botPs);
        assertThat(step2.actionType()).isEqualTo(GameActionType.ACTIVATE_EFFECT);
        assertThat(step2.cardInstanceId()).isEqualTo("FAIRY_1");
        engine.processAction(botId, step2);
        assertThat(botPs.getEnergyTokens()).isEqualTo(5);

        // Step 3: With 5⚡ unlocked -> summons LIGHT_M_09 (Golem de Lumière)!
        GameActionCommand step3 = bot.chooseNextTurnAction(room, botPs);
        assertThat(step3.actionType()).isEqualTo(GameActionType.PLAY_CARD);
        assertThat(botPs.findInHand(step3.cardInstanceId()).orElseThrow().getDefinition().cardId()).isEqualTo("LIGHT_M_09");
    }

    @Test
    void hardBotSequencesFamiliarBeforePayoffAndOnSpellCastBeforeSpells() {
        // Part A: Familiar before ALLY_FAMILIAR_PRESENT (Chevalier de l'Aube)
        UUID botIdA = UUID.randomUUID();
        GameRoom roomA = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdA, "HardA", true, TeamId.TEAM_A,
                        customDeck("LIGHT_M_03", "LIGHT_F_01", "LIGHT_M_03", "LIGHT_M_03", "LIGHT_M_03"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(UUID.randomUUID(), "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.ICE))));
        completeMulliganForAll(roomA);
        PlayerState psA = roomA.getPlayer(botIdA);
        psA.addEnergy(2); // total 3 -> enough for LIGHT_F_01 (1) + LIGHT_M_03 (2)

        GameActionCommand firstPlayA = bot.chooseNextTurnAction(roomA, psA);
        assertThat(firstPlayA.actionType()).isEqualTo(GameActionType.PLAY_CARD);
        assertThat(psA.findInHand(firstPlayA.cardInstanceId()).orElseThrow().getDefinition().cardId()).isEqualTo("LIGHT_F_01");

        // Part B: ON_SPELL_CAST monster (Serpent de Glace, cost 2) BEFORE casting Chaînes de Glace (cost 2) on Turn 2+
        UUID botIdB = UUID.randomUUID();
        UUID oppIdB = UUID.randomUUID();
        GameRoom roomB = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdB, "HardB", true, TeamId.TEAM_A,
                        customDeck("ICE_S_02", "ICE_M_03", "ICE_S_02", "ICE_S_02", "ICE_S_02"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppIdB, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.FIRE))));
        completeMulliganForAll(roomB);
        roomB.incrementRoundNumber(); // Past Turn 1 Truce

        PlayerState psB = roomB.getPlayer(botIdB);
        psB.addEnergy(3); // total 4 -> ICE_M_03 (2) + ICE_S_02 (2)
        CardInstance oppMon = new CardInstance("OPP_B", oppIdB, card("FIRE_M_03"), false);
        oppMon.setSlot(0);
        oppMon.setPosture(Posture.ATTACK);
        roomB.getPlayer(oppIdB).getMonsters()[0] = oppMon;

        GameActionCommand firstPlayB = bot.chooseNextTurnAction(roomB, psB);
        assertThat(firstPlayB.actionType()).isEqualTo(GameActionType.PLAY_CARD);
        assertThat(psB.findInHand(firstPlayB.cardInstanceId()).orElseThrow().getDefinition().cardId()).isEqualTo("ICE_M_03");
    }

    @Test
    void hardBotDetectsAndExecutesMultiStepLethalWithClearPostureFlipAndDirectAttacks() {
        UUID botId = UUID.randomUUID();
        UUID oppId = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botId, "HardLethal", true, TeamId.TEAM_A,
                        customDeck("FIRE_S_03", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppId, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));
        completeMulliganForAll(room);
        room.incrementRoundNumber(); // Past Turn 1 Truce

        PlayerState botPs = room.getPlayer(botId);
        botPs.addEnergy(1); // total 2 -> can cast FIRE_S_03 (Boule de Feu)
        // Opponent at 5 LP guarded by LIGHT_M_02 (GARDIEN 2/3 DEFENSE)
        room.getTeam(TeamId.TEAM_B).applyDamage(20);
        CardInstance guard = new CardInstance("GUARD_1", oppId, card("LIGHT_M_02"), false);
        guard.setSlot(0);
        guard.setPosture(Posture.DEFENSE);
        room.getPlayer(oppId).getMonsters()[0] = guard;

        // Bot has Griffon de Feu (3/4 DEFENSE, needs posture flip!) and Bélier de Magma (3/3 ATTACK)
        CardInstance griffon = new CardInstance("GRIFF_1", botId, card("FIRE_M_06"), false);
        griffon.setSlot(0);
        griffon.setPosture(Posture.DEFENSE);
        botPs.getMonsters()[0] = griffon;

        CardInstance belier = new CardInstance("BEL_1", botId, card("FIRE_M_05"), false);
        belier.setSlot(1);
        belier.setPosture(Posture.ATTACK);
        botPs.getMonsters()[1] = belier;

        // Execute up to 8 bot actions until turn concludes or match ends
        for (int i = 0; i < 8 && !room.isFinished(); i++) {
            GameActionCommand cmd = bot.chooseNextTurnAction(room, botPs);
            engine.processAction(botId, cmd);
            if (cmd.actionType() == GameActionType.END_TURN) {
                break;
            }
        }

        // Bot cleared Gardien with FIRE_S_03(0), flipped Griffon DEF->ATTACK, and pushed 6 >= 5 lethal damage!
        assertThat(room.isFinished()).isTrue();
        assertThat(room.getWinnerTeam()).isEqualTo(TeamId.TEAM_A);
        assertThat(room.getTeam(TeamId.TEAM_B).getLp()).isLessThanOrEqualTo(0);
    }

    @Test
    void hardBotUsesDualTargetSlowAndAvoidsWastedCleanseAndUsesGolemDefensePosture() {
        UUID botId = UUID.randomUUID();
        UUID oppId = UUID.randomUUID();
        GameRoom room = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botId, "HardTactic", true, TeamId.TEAM_A,
                        customDeck("ICE_M_09", "LIGHT_S_01", "LIGHT_S_02", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppId, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.FIRE))));
        completeMulliganForAll(room);

        PlayerState botPs = room.getPlayer(botId);
        botPs.addEnergy(5); // 6 energy
        CardInstance iceDragon = new CardInstance("ICEDRAGON", botId, card("ICE_M_10"), false);
        iceDragon.setSlot(0);
        iceDragon.setPosture(Posture.ATTACK);
        botPs.getMonsters()[0] = iceDragon;

        PlayerState oppPs = room.getPlayer(oppId);
        CardInstance threat1 = new CardInstance("OPP_1", oppId, card("FIRE_M_05"), false);
        threat1.setSlot(0);
        threat1.setPosture(Posture.ATTACK);
        oppPs.getMonsters()[0] = threat1;

        CardInstance threat2 = new CardInstance("OPP_2", oppId, card("FIRE_M_06"), false);
        threat2.setSlot(1);
        threat2.setPosture(Posture.ATTACK);
        oppPs.getMonsters()[1] = threat2;

        // 1. Activate Dragon des Glaces passing BOTH targetId AND secondaryTargetId!
        GameActionCommand slowCmd = bot.chooseNextTurnAction(room, botPs);
        assertThat(slowCmd.actionType()).isEqualTo(GameActionType.ACTIVATE_EFFECT);
        assertThat(slowCmd.cardInstanceId()).isEqualTo("ICEDRAGON");
        assertThat(slowCmd.targetId()).isIn("OPP_1", "OPP_2");
        assertThat(slowCmd.secondaryTargetId()).isIn("OPP_1", "OPP_2");
        assertThat(slowCmd.secondaryTargetId()).isNotEqualTo(slowCmd.targetId());
        engine.processAction(botId, slowCmd);
        assertThat(threat1.isSlowedCurrentTurn()).isTrue();
        assertThat(threat2.isSlowedCurrentTurn()).isTrue();

        // Remove Epic ICEDRAGON so Epic Golem de Glace (ICE_M_09) is legally summonable under the 1-Epic rule
        botPs.getMonsters()[0] = null;

        // 2. Summons Golem de Glace (ICE_M_09) in DEFENSE posture (activation of SPELL_DESTRUCTION_IMMUNITY!)
        GameActionCommand summonGolem = bot.chooseNextTurnAction(room, botPs);
        assertThat(summonGolem.actionType()).isEqualTo(GameActionType.PLAY_CARD);
        assertThat(botPs.findInHand(summonGolem.cardInstanceId()).orElseThrow().getDefinition().cardId()).isEqualTo("ICE_M_09");
        assertThat(summonGolem.posture()).isEqualTo(Posture.DEFENSE);
        engine.processAction(botId, summonGolem);

        // 3. Remove any leftover cheap summons so only LIGHT_S_01 & LIGHT_S_02 remain in hand;
        //    with clean board & full 25/25 LP, HARD preserves both instead of wasting them!
        botPs.getHand().removeIf(c -> {
            String id = c.getDefinition().cardId();
            return !"LIGHT_S_01".equals(id) && !"LIGHT_S_02".equals(id);
        });
        botPs.addEnergy(2); // plenty of energy to cast either spell if desired
        GameActionCommand after = bot.chooseNextTurnAction(room, botPs);
        assertThat(after.actionType()).isEqualTo(GameActionType.END_TURN);
    }

    @Test
    void hardBotFiresShadowDemonBeforeWipingLastEnemyFamiliarAndPairsAttackersCleanly() {
        // Part A: SHADOW_M_08 (+2 LP if opp has familiar) must fire BEFORE Mini-Banshee sweeps last 1-DEF familiar!
        UUID botIdA = UUID.randomUUID();
        UUID oppIdA = UUID.randomUUID();
        GameRoom roomA = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdA, "HardDemon", true, TeamId.TEAM_A,
                        customDeck("FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppIdA, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));
        completeMulliganForAll(roomA);

        PlayerState psA = roomA.getPlayer(botIdA);
        psA.getHand().clear();
        CardInstance demon = new CardInstance("DEMON_1", botIdA, card("SHADOW_M_08"), false);
        demon.setSlot(0);
        demon.setPosture(Posture.ATTACK);
        psA.getMonsters()[0] = demon;

        CardInstance banshee = new CardInstance("BANSH_1", botIdA, card("ICE_F_10"), false);
        banshee.setSlot(0);
        banshee.setPosture(Posture.ATTACK);
        psA.getFamiliars()[0] = banshee;

        CardInstance oppFam = new CardInstance("OPPFAM_1", oppIdA, card("LIGHT_F_02"), false); // 1/1 familiar
        oppFam.setSlot(0);
        oppFam.setPosture(Posture.ATTACK);
        roomA.getPlayer(oppIdA).getFamiliars()[0] = oppFam;

        GameActionCommand first = bot.chooseNextTurnAction(roomA, psA);
        assertThat(first.actionType()).isEqualTo(GameActionType.ACTIVATE_EFFECT);
        assertThat(first.cardInstanceId()).isEqualTo("DEMON_1");
        engine.processAction(botIdA, first);
        assertThat(roomA.getTeam(TeamId.TEAM_B).getLp()).isEqualTo(23);

        // Now after DEMON_1 already fired, BANSH_1 sweeps the 1-DEF familiar cleanly!
        GameActionCommand second = bot.chooseNextTurnAction(roomA, psA);
        assertThat(second.actionType()).isEqualTo(GameActionType.ACTIVATE_EFFECT);
        assertThat(second.cardInstanceId()).isEqualTo("BANSH_1");

        // Part B: Combat pairing - 4 ATK clears 3 DEF obstacle cleanly while 3 ATK clears 1 DEF obstacle
        UUID botIdB = UUID.randomUUID();
        UUID oppIdB = UUID.randomUUID();
        GameRoom roomB = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdB, "HardCombat", true, TeamId.TEAM_A,
                        customDeck("FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01", "FIRE_M_01"), AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppIdB, "Opp", false, TeamId.TEAM_B, catalog.getStarterDeckCards(Faction.LIGHT))));
        completeMulliganForAll(roomB);
        roomB.incrementRoundNumber();

        PlayerState psB = roomB.getPlayer(botIdB);
        psB.getHand().clear();
        CardInstance strongAtk = new CardInstance("ATK_4", botIdB, card("ICE_M_07"), false); // 4 ATK
        strongAtk.setSlot(0);
        strongAtk.setPosture(Posture.ATTACK);
        psB.getMonsters()[0] = strongAtk;

        CardInstance medAtk = new CardInstance("ATK_3", botIdB, card("FIRE_M_05"), false); // 3 ATK
        medAtk.setSlot(1);
        medAtk.setPosture(Posture.ATTACK);
        psB.getMonsters()[1] = medAtk;

        PlayerState oppB = roomB.getPlayer(oppIdB);
        CardInstance def3Mon = new CardInstance("DEF_3", oppIdB, card("LIGHT_M_03"), false); // 3/3 -> DEF 3 in DEFENSE
        def3Mon.setSlot(0);
        def3Mon.setPosture(Posture.DEFENSE);
        oppB.getMonsters()[0] = def3Mon;

        CardInstance def1Mon = new CardInstance("DEF_1", oppIdB, card("SHADOW_M_01"), false); // 2/1 -> DEF 1 in DEFENSE
        def1Mon.setSlot(1);
        def1Mon.setPosture(Posture.DEFENSE);
        oppB.getMonsters()[1] = def1Mon;

        // Execute both combat attacks cleanly; both defenders should be destroyed with 0 recoil!
        GameActionCommand atk1 = bot.chooseNextTurnAction(roomB, psB);
        assertThat(atk1.actionType()).isEqualTo(GameActionType.DECLARE_ATTACK);
        engine.processAction(botIdB, atk1);
        GameActionCommand atk2 = bot.chooseNextTurnAction(roomB, psB);
        assertThat(atk2.actionType()).isEqualTo(GameActionType.DECLARE_ATTACK);
        engine.processAction(botIdB, atk2);

        assertThat(oppB.getAllMonsters()).isEmpty();
        assertThat(psB.getAllMonsters()).hasSize(2);
        assertThat(roomB.getTeam(TeamId.TEAM_A).getLp()).isEqualTo(25);
    }

    @Test
    void hardBotRespectsFogOfWarNeverReadingOpponentHandDeckOrFaceDownSpells() {
        UUID botIdA = UUID.randomUUID();
        UUID oppIdA = UUID.randomUUID();
        UUID botIdB = UUID.randomUUID();
        UUID oppIdB = UUID.randomUUID();

        List<CardDefinition> botDeck = customDeck("FIRE_S_03", "FIRE_M_05", "FIRE_F_01", "FIRE_S_01", "FIRE_M_03");
        List<CardDefinition> oppDeckA = customDeck("ICE_M_05", "ICE_M_09", "ICE_S_02", "ICE_F_09", "ICE_S_10");
        List<CardDefinition> oppDeckB = customDeck("SHADOW_M_10", "SHADOW_S_07", "SHADOW_F_03", "SHADOW_S_10", "SHADOW_M_08");

        GameRoom roomA = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdA, "HardA", true, TeamId.TEAM_A, botDeck, AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppIdA, "OppA", false, TeamId.TEAM_B, oppDeckA)));
        GameRoom roomB = engine.createMatch(GameMode.DUEL_1V1, false, true, false, false, List.of(
                MatchParticipantSpec.simple(botIdB, "HardB", true, TeamId.TEAM_A, botDeck, AiDifficulty.HARD),
                MatchParticipantSpec.simple(oppIdB, "OppB", false, TeamId.TEAM_B, oppDeckB)));

        completeMulliganForAll(roomA);
        completeMulliganForAll(roomB);

        // Align identical public board & synchronize own hand IDs so choices depend ONLY on public state + own hand
        PlayerState oppA = roomA.getPlayer(oppIdA);
        PlayerState oppB = roomB.getPlayer(oppIdB);
        Collections.reverse(oppB.getDeck());
        CardInstance fdA = new CardInstance("FD_1", oppIdA, card("FIRE_S_04"), false);
        fdA.setSlot(0);
        fdA.setFaceDown(true);
        oppA.getSpellArtifacts()[0] = fdA;

        CardInstance fdB = new CardInstance("FD_1", oppIdB, card("SHADOW_S_02"), false);
        fdB.setSlot(0);
        fdB.setFaceDown(true);
        oppB.getSpellArtifacts()[0] = fdB;

        PlayerState botA = roomA.getPlayer(botIdA);
        PlayerState botB = roomB.getPlayer(botIdB);
        botB.getHand().clear();
        for (CardInstance c : botA.getHand()) {
            botB.getHand().add(new CardInstance(c.getInstanceId(), botIdB, c.getDefinition(), c.isFoil()));
        }

        GameActionCommand actA = bot.chooseNextTurnAction(roomA, botA);
        GameActionCommand actB = bot.chooseNextTurnAction(roomB, botB);

        assertThat(actB.actionType()).isEqualTo(actA.actionType());
        assertThat(actB.cardInstanceId()).isEqualTo(actA.cardInstanceId());
        assertThat(actB.posture()).isEqualTo(actA.posture());
        assertThat(actB.choiceIndex()).isEqualTo(actA.choiceIndex());
    }

    @Test
    void hardAndMediumBotsCompleteFullDuelsAnd2v2MatchesAcrossAllFactionsCleanly() {
        // 1. SHADOW (HARD) vs LIGHT (MEDIUM) 1v1
        GameRoom duel = engine.createMatch(GameMode.DUEL_1V1, false, true, false, true, List.of(
                MatchParticipantSpec.simple(UUID.randomUUID(), "OmbreHard", true, TeamId.TEAM_A,
                        catalog.getStarterDeckCards(Faction.SHADOW), AiDifficulty.HARD),
                MatchParticipantSpec.simple(UUID.randomUUID(), "LumiereMed", true, TeamId.TEAM_B,
                        catalog.getStarterDeckCards(Faction.LIGHT), AiDifficulty.MEDIUM)));
        engine.runBotLoopSynchronouslyForTest(duel.getMatchId());
        assertThat(duel.getTurnNumber()).isGreaterThan(1);
        assertThat(duel.getCombatLog()).isNotEmpty();

        // 2. 2v2 Alliance (FIRE HARD + ICE MEDIUM vs SHADOW MEDIUM + LIGHT HARD)
        GameRoom teamMatch = engine.createMatch(GameMode.TEAM_2V2, false, true, false, true, List.of(
                MatchParticipantSpec.simple(UUID.randomUUID(), "FeuHard", true, TeamId.TEAM_A,
                        catalog.getStarterDeckCards(Faction.FIRE), AiDifficulty.HARD),
                MatchParticipantSpec.simple(UUID.randomUUID(), "GlaceMed", true, TeamId.TEAM_A,
                        catalog.getStarterDeckCards(Faction.ICE), AiDifficulty.MEDIUM),
                MatchParticipantSpec.simple(UUID.randomUUID(), "OmbreMed", true, TeamId.TEAM_B,
                        catalog.getStarterDeckCards(Faction.SHADOW), AiDifficulty.MEDIUM),
                MatchParticipantSpec.simple(UUID.randomUUID(), "LumiereHard", true, TeamId.TEAM_B,
                        catalog.getStarterDeckCards(Faction.LIGHT), AiDifficulty.HARD)));
        engine.runBotLoopSynchronouslyForTest(teamMatch.getMatchId());
        assertThat(teamMatch.getTurnNumber()).isGreaterThan(1);
        assertThat(teamMatch.getCombatLog()).isNotEmpty();
    }
}
