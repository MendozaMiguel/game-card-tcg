package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.AbilityOption;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.engine.AbilityInterpreter.AbilityContext;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PendingModalChoice;
import com.ordredesarcane.online.game.model.PendingReactionWindow;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.StatModifier;
import com.ordredesarcane.online.game.model.TeamId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Strategic and combo-driven decision planner backing {@link MageAutomateBot}.
 * <p>
 * Supports all three difficulty tiers:
 * <ul>
 *   <li>{@link AiDifficulty#EASY}: beginner-friendly heuristics, keeps opening hand, simple card order.</li>
 *   <li>{@link AiDifficulty#MEDIUM}: solid combo sequencing, board-condition ordering, safe combat &amp; ramp.</li>
 *   <li>{@link AiDifficulty#HARD}: full-turn lethal solver, unified cross-action lookahead, synergy chains,
 *   optimal multi-attacker pairing, reaction economy, and defensive/posture foresight.</li>
 * </ul>
 */
final class BotTacticalPlanner {

    private final BoardRuleEvaluator evaluator;
    private final AbilityInterpreter abilities;
    private final CombatResolver combatResolver;
    private final FogOfWarProjector projector;
    private final BotLethalSolver lethalSolver;

    BotTacticalPlanner(
            BoardRuleEvaluator evaluator,
            AbilityInterpreter abilities,
            CombatResolver combatResolver,
            FogOfWarProjector projector) {
        this.evaluator = evaluator;
        this.abilities = abilities;
        this.combatResolver = combatResolver;
        this.projector = projector;
        this.lethalSolver = new BotLethalSolver(evaluator, abilities);
    }

    GameActionCommand chooseMulliganAction(GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        if (difficulty == AiDifficulty.EASY) {
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.MULLIGAN_SWAP,
                    null, null, null, null, null, null, List.of());
        }

        List<CardInstance> hand = bot.getHand();
        if (difficulty == AiDifficulty.MEDIUM) {
            List<String> toReplace = hand.stream()
                    .filter(c -> c.getDefinition().energyCost() >= 5
                            || (c.getDefinition().energyCost() >= 4 && c.getDefinition().cardType() == CardType.SPELL_ULTIMATE))
                    .limit(2)
                    .map(CardInstance::getInstanceId)
                    .toList();
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.MULLIGAN_SWAP,
                    null, null, null, null, null, null, toReplace);
        }

        // HARD: optimize opening curve & keep 2-card synergy pairs (ramp/enabler + payoff)
        boolean hasCheapFamiliar = hand.stream()
                .anyMatch(c -> c.getDefinition().cardType() == CardType.FAMILIAR && c.getDefinition().energyCost() <= 2);
        boolean hasCheapMonster = hand.stream()
                .anyMatch(c -> c.getDefinition().cardType() == CardType.MONSTER && c.getDefinition().energyCost() <= 3);
        boolean hasRamp = hand.stream()
                .anyMatch(c -> c.getDefinition().energyCost() <= 3 && (BotComboKnowledge.isTurnStartRamp(c.getDefinition())
                        || "FIRE_F_05".equals(c.getDefinition().cardId())
                        || "SHADOW_F_02".equals(c.getDefinition().cardId())
                        || "ICE_F_02".equals(c.getDefinition().cardId())));

        int epicCount = 0;
        List<String> toReplace = new ArrayList<>();
        for (CardInstance c : hand) {
            CardDefinition def = c.getDefinition();
            int cost = def.energyCost();
            boolean replace = false;
            if (def.isEpic()) {
                epicCount++;
                if (epicCount > 1 || !hasRamp) {
                    replace = true;
                }
            } else if (cost >= 5) {
                replace = true;
            } else if (cost == 4 && (!hasRamp || def.cardType() == CardType.CURSE || def.cardType() == CardType.SPELL_ULTIMATE)) {
                replace = true;
            } else if (cost >= 3 && def.cardType() == CardType.SPELL_REACTIVE && !hasCheapMonster) {
                replace = true;
            } else if (BotComboKnowledge.isAnyCondition(def, "ALLY_FAMILIAR_PRESENT") && !hasCheapFamiliar && cost >= 4) {
                replace = true;
            }
            if (replace && toReplace.size() < 3) {
                toReplace.add(c.getInstanceId());
            }
        }
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.MULLIGAN_SWAP,
                null, null, null, null, null, null, toReplace);
    }

    GameActionCommand chooseDiscardAction(GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        int excess = Math.max(0, bot.getHand().size() - PlayerState.MAX_HAND_SIZE_END_TURN);
        if (excess == 0) {
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.DISCARD_EXCESS,
                    null, null, null, null, null, null, List.of());
        }

        Comparator<CardInstance> discardOrder;
        if (difficulty == AiDifficulty.EASY) {
            discardOrder = Comparator.comparingInt(c -> c.getDefinition().energyCost());
        } else if (difficulty == AiDifficulty.MEDIUM) {
            discardOrder = Comparator
                    .<CardInstance>comparingInt(c -> c.getDefinition().energyCost())
                    .reversed()
                    .thenComparingInt(c -> c.getDefinition().cardType().isSpellCategory() ? 0 : 1);
        } else {
            discardOrder = Comparator.comparingInt(c -> evaluateHandRetentionValue(room, bot, c));
        }

        List<String> dropIds = bot.getHand().stream()
                .sorted(discardOrder)
                .limit(excess)
                .map(CardInstance::getInstanceId)
                .toList();
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.DISCARD_EXCESS,
                null, null, null, null, null, null, dropIds);
    }

    GameActionCommand chooseReactionAction(GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        PendingReactionWindow rw = room.getPendingReaction();
        if (rw == null) {
            return passReaction(room);
        }
        List<String> eligible = projector.findEligibleReactiveCardsForResponder(room, bot, rw);
        if (eligible.isEmpty()) {
            return passReaction(room);
        }

        if (difficulty != AiDifficulty.HARD) {
            return pickSimpleReaction(room, bot, rw, eligible, difficulty);
        }

        // HARD: score every eligible reaction card against passing
        String bestCardId = null;
        int bestScore = 0;
        Integer bestChoice = null;
        String bestTarget = null;

        for (String id : eligible) {
            Optional<CardInstance> opt = bot.findOnBoard(id);
            if (opt.isEmpty()) {
                continue;
            }
            CardInstance card = opt.get();
            ReactionEval eval = evaluateReactionOption(room, bot, rw, card);
            if (eval.score() > bestScore) {
                bestScore = eval.score();
                bestCardId = id;
                bestChoice = eval.choiceIndex();
                bestTarget = eval.targetId();
            }
        }

        if (bestCardId == null) {
            return passReaction(room);
        }
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.TRIGGER_REACTION,
                bestCardId, null, null, bestTarget, null, bestChoice, null);
    }

    GameActionCommand chooseModalAction(GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        PendingModalChoice pc = room.getPendingChoice();
        if (pc == null || pc.options() == null || pc.options().isEmpty()) {
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.RESOLVE_CHOICE,
                    null, null, null, null, null, 0, null);
        }

        int choiceIdx = pickModalOptionForCard(room, bot, pc.sourceInstanceId(), pc.options(), difficulty);
        String targetId = resolveTargetForModalOption(room, bot, pc.options().get(choiceIdx));
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.RESOLVE_CHOICE,
                null, null, null, targetId, null, choiceIdx, null);
    }

    GameActionCommand chooseNextTurnAction(GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        if (difficulty == AiDifficulty.EASY) {
            return chooseEasyTurnAction(room, bot);
        }

        // 1. HARD full-turn lethal hunter
        if (difficulty == AiDifficulty.HARD && !room.isTurn1TruceActive()) {
            Optional<GameActionCommand> lethalCmd = lethalSolver.findLethalAction(room, bot);
            if (lethalCmd.isPresent()) {
                return lethalCmd.get();
            }
        }

        // 2. Unified MAIN_PHASE combo, activation, play, reactive set, and posture planner
        if (room.getPhase() == GamePhase.MAIN_PHASE) {
            Optional<GameActionCommand> bestMainCmd = chooseBestMainPhaseAction(room, bot, difficulty);
            if (bestMainCmd.isPresent()) {
                return bestMainCmd.get();
            }
        }

        // 3. Optimal multi-attacker combat pairing
        if (!room.isTurn1TruceActive()) {
            Optional<GameActionCommand> attackCmd = chooseBestCombatAttack(room, bot, difficulty);
            if (attackCmd.isPresent()) {
                return attackCmd.get();
            }

            // Before ending turn in MAIN_PHASE, trigger SHADOW_M_09 (Dragon Ombre) ON_COMBAT_PHASE_START burn if active!
            if (room.getPhase() == GamePhase.MAIN_PHASE
                    && !room.isCombatPhaseStartedThisTurn()
                    && BotComboKnowledge.anyFamiliarOnAnyBoard(room)
                    && bot.getAllMonsters().stream().anyMatch(m -> "SHADOW_M_09".equals(m.getDefinition().cardId()))) {
                return new GameActionCommand(
                        room.getMatchId(), null, GameActionType.ENTER_COMBAT,
                        null, null, null, null, null, null, null);
            }
        }

        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.END_TURN,
                null, null, null, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // EASY Tier
    // -------------------------------------------------------------------------

    private GameActionCommand chooseEasyTurnAction(GameRoom room, PlayerState bot) {
        if (room.getPhase() == GamePhase.MAIN_PHASE) {
            Optional<GameActionCommand> playCmd = bot.getHand().stream()
                    .filter(c -> canPlayCardSafely(room, bot, c, null))
                    .max(Comparator.comparingInt(c -> scorePlayCardBasic(room, bot, c)))
                    .map(c -> buildPlayCommand(room, bot, c, AiDifficulty.EASY));
            if (playCmd.isPresent()) {
                return playCmd.get();
            }

            if (room.getTeam(bot.getTeamId()).getLp() <= 12 && !bot.isSpellsLockedThisTurn()) {
                int slot = bot.findFirstFreeSpellSlot();
                if (slot >= 0) {
                    Optional<CardInstance> reactive = bot.getHand().stream()
                            .filter(c -> c.getDefinition().cardType() == CardType.SPELL_REACTIVE
                                    && c.getDefinition().energyCost() <= bot.getEnergyTokens())
                            .findFirst();
                    if (reactive.isPresent()) {
                        return new GameActionCommand(
                                room.getMatchId(), null, GameActionType.SET_REACTIVE_SPELL,
                                reactive.get().getInstanceId(), slot, null, null, null, null, null);
                    }
                }
            }
        }

        if (!room.isTurn1TruceActive()) {
            Optional<GameActionCommand> atk = chooseBasicAttack(room, bot);
            if (atk.isPresent()) {
                return atk.get();
            }
        }

        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.END_TURN,
                null, null, null, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // Unified MAIN_PHASE Action Scorer (MEDIUM & HARD)
    // -------------------------------------------------------------------------

    private Optional<GameActionCommand> chooseBestMainPhaseAction(
            GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        List<ScoredCommand> candidates = new ArrayList<>();
        int unlockableEnergy = BotComboKnowledge.computeUnlockableFreeEnergy(
                room, bot, evaluator, true, difficulty == AiDifficulty.HARD);
        int potentialTotalEnergy = bot.getEnergyTokens() + unlockableEnergy;

        evaluateBoardActivationCandidates(room, bot, difficulty, potentialTotalEnergy, candidates);
        evaluateHandPlayCandidates(room, bot, difficulty, potentialTotalEnergy, candidates);
        evaluateReactiveSetCandidates(room, bot, difficulty, candidates);
        evaluatePostureSwitchCandidates(room, bot, difficulty, candidates);

        return candidates.stream()
                .filter(sc -> sc.score() > 0)
                .max(Comparator.comparingInt(ScoredCommand::score))
                .map(ScoredCommand::command);
    }

    private void evaluateBoardActivationCandidates(
            GameRoom room,
            PlayerState bot,
            AiDifficulty difficulty,
            int potentialTotalEnergy,
            List<ScoredCommand> out) {
        boolean preserveSingleOppFam = BotComboKnowledge.shouldPreserveSingleOpponentFamiliar(room, bot, evaluator);
        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        int maxLp = room.getTeam(bot.getTeamId()).getMaxLp();

        for (CardInstance card : bot.getAllBoardCreatures()) {
            if (card.isFaceDown() || card.isActivatedThisTurn() || card.isExhausted()) {
                continue;
            }
            CardDefinition def = card.getDefinition();
            List<AbilityDefinition> activated = def.abilitiesScript().stream()
                    .filter(a -> "ACTIVATED".equals(a.trigger()))
                    .toList();
            if (activated.isEmpty()) {
                continue;
            }
            AbilityDefinition primary = activated.getFirst();
            if (!evaluator.checkCondition(room, bot, card, primary.condition(), null)) {
                continue;
            }

            String cid = def.cardId();
            if ("SHADOW_F_10".equals(cid) && abilities.findTargetCandidates(room, bot, card, "OPPONENT_FAMILIAR").isEmpty()) {
                continue;
            }

            // 1. Free energy & ramp chains
            if ("LIGHT_F_04".equals(cid)) {
                out.add(new ScoredCommand(2100, activateCmd(room, card.getInstanceId(), null, null, null)));
                continue;
            }
            if ("SHADOW_F_02".equals(cid)) {
                if (!card.isFirstCamouflageUsed()) {
                    out.add(new ScoredCommand(2050, activateCmd(room, card.getInstanceId(), null, null, null)));
                } else if (difficulty == AiDifficulty.HARD && !card.isCamouflaged() && isSafeToStealth(room, bot, card)) {
                    out.add(new ScoredCommand(220, activateCmd(room, card.getInstanceId(), null, null, null)));
                }
                continue;
            }
            if ("ICE_F_02".equals(cid)) {
                int s = teamLp >= 20 ? 1980 : (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot) ? 1920 : (teamLp < maxLp ? 310 : 0));
                if (s > 0) {
                    out.add(new ScoredCommand(s, activateCmd(room, card.getInstanceId(), null, null, null)));
                }
                continue;
            }
            if ("FIRE_F_05".equals(cid)) {
                int sacrificeGain = evaluateEnergyUnlockDemand(room, bot, 2, difficulty);
                if (sacrificeGain > 0) {
                    // Penalize breaking ALLY_FAMILIAR_PRESENT on own board if Esprit is the only familiar
                    int depPenalty = (bot.getAllFamiliars().size() == 1 && bot.getAllMonsters().stream()
                            .anyMatch(m -> BotComboKnowledge.isAnyCondition(m.getDefinition(), "ALLY_FAMILIAR_PRESENT")))
                            ? 450 : 0;
                    int finalScore = sacrificeGain - depPenalty;
                    if (finalScore > 120) {
                        out.add(new ScoredCommand(1850 + finalScore, activateCmd(room, card.getInstanceId(), null, null, null)));
                    }
                }
                continue;
            }
            if ("SHADOW_F_03".equals(cid)) {
                int pactDemand = evaluateEnergyUnlockDemand(room, bot, 1, difficulty);
                if (pactDemand > 0 && teamLp > 6) {
                    out.add(new ScoredCommand(1900 + pactDemand, activateCmd(room, card.getInstanceId(), null, null, 0)));
                } else if (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot)) {
                    out.add(new ScoredCommand(1900, activateCmd(room, card.getInstanceId(), null, null, 1)));
                } else if (teamLp < maxLp) {
                    out.add(new ScoredCommand(260, activateCmd(room, card.getInstanceId(), null, null, 1)));
                }
                continue;
            }
            if (BotComboKnowledge.hasAbility(def, "ACTIVATED", "HEAL_LP")) {
                if (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot)) {
                    out.add(new ScoredCommand(1960, activateCmd(room, card.getInstanceId(), null, null, null)));
                } else if (teamLp < maxLp) {
                    out.add(new ScoredCommand(340, activateCmd(room, card.getInstanceId(), null, null, null)));
                }
                continue;
            }

            // 2. Shadow Demon LP burn BEFORE any familiar sweep!
            if ("SHADOW_M_08".equals(cid)) {
                out.add(new ScoredCommand(1650, activateCmd(room, card.getInstanceId(), null, null, null)));
                continue;
            }

            // 3. Mini-Dragon modal activation (-1 ATK this turn if flips a trade, else 1 LP)
            if ("FIRE_F_07".equals(cid)) {
                CardInstance bestTradeShift = findEnemyAtkDebuffFlippingCombatTrade(room, bot, 1);
                if (difficulty == AiDifficulty.HARD && !room.isTurn1TruceActive() && bestTradeShift != null) {
                    out.add(new ScoredCommand(620, activateCmd(room, card.getInstanceId(), bestTradeShift.getInstanceId(), null, 1)));
                } else {
                    out.add(new ScoredCommand(460, activateCmd(room, card.getInstanceId(), null, null, 0)));
                }
                continue;
            }

            // 4. Slow / Dual Slow (Dragon des Glaces up to 2!)
            if ("ICE_M_10".equals(cid)) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "UP_TO_2_OPPONENT_CREATURES")
                        .stream()
                        .filter(e -> !e.isSlowedCurrentTurn())
                        .sorted(Comparator.<CardInstance>comparingInt(
                                e -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, e)).reversed())
                        .toList();
                if (!cands.isEmpty()) {
                    String t1 = cands.getFirst().getInstanceId();
                    String t2 = cands.size() > 1 ? cands.get(1).getInstanceId() : null;
                    int score = 700 + cands.stream().limit(2)
                            .mapToInt(e -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, e) * 3)
                            .sum();
                    out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), t1, t2, null)));
                }
                continue;
            }
            if ("APPLY_SLOW".equals(primary.action())) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, primary.target())
                        .stream()
                        .filter(e -> !e.isSlowedCurrentTurn() && evaluator.evaluateEffectiveAtk(room, e) > 0)
                        .toList();
                if (!cands.isEmpty()) {
                    CardInstance best = cands.stream()
                            .max(Comparator.comparingInt(e -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, e)))
                            .orElseThrow();
                    int score = 480 + BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, best) * 2;
                    out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), best.getInstanceId(), null, null)));
                }
                continue;
            }

            // 5. Stealth familiars
            if ("GRANT_CAMOUFLAGE".equals(primary.action())) {
                if (!card.isCamouflaged() && isSafeToStealth(room, bot, card)) {
                    out.add(new ScoredCommand(210, activateCmd(room, card.getInstanceId(), card.getInstanceId(), null, null)));
                }
                continue;
            }

            // 6. Peek Opponent Top Card (Hibou Polaire / Chouette de Feu)
            if ("PEEK_OPPONENT_TOP_CARD_BUFF_IF_SPELL".equals(primary.action())
                    || "PEEK_OPPONENT_TOP_CARD".equals(primary.action())) {
                List<PlayerState> opps = room.getOpponentPlayers(bot.getTeamId());
                if (!opps.isEmpty() && !opps.getFirst().getDeck().isEmpty()) {
                    CardInstance allyMon = pickBestAllyMonsterToBuffDef(room, bot, null);
                    String secId = allyMon != null ? allyMon.getInstanceId() : null;
                    boolean alreadyPeeked = bot.getPeekedOpponentTopCards().containsKey(opps.getFirst().getUserId());
                    int score = "PEEK_OPPONENT_TOP_CARD_BUFF_IF_SPELL".equals(primary.action())
                            ? (allyMon != null ? 290 : 120)
                            : (alreadyPeeked ? 0 : 190);
                    if (score > 0) {
                        out.add(new ScoredCommand(score, activateCmd(
                                room, card.getInstanceId(), opps.getFirst().getUserId().toString(), secId, null)));
                    }
                }
                continue;
            }

            // 7. AoE Familiars / All Monsters debuffs
            if ("ALL_OPPONENT_FAMILIARS".equals(primary.target())) {
                List<CardInstance> oppFams = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_FAMILIARS", false, AbilityContext.empty());
                if (!oppFams.isEmpty()) {
                    boolean wouldWipeLast = oppFams.size() == 1 && evaluator.evaluateEffectiveDef(room, oppFams.getFirst()) <= 1;
                    if (!wouldWipeLast || !preserveSingleOppFam) {
                        int kills = (int) oppFams.stream().filter(f -> evaluator.evaluateEffectiveDef(room, f) <= 1).count();
                        int score = 460 + oppFams.size() * 140 + kills * 320;
                        out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), null, null, null)));
                    }
                }
                continue;
            }
            if ("ALL_OPPONENT_MONSTERS".equals(primary.target())) {
                List<CardInstance> oppMons = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_MONSTERS", false, AbilityContext.empty());
                if (!oppMons.isEmpty()) {
                    int kills = (int) oppMons.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= 1).count();
                    int score = (!room.isTurn1TruceActive() || kills > 0) ? (520 + oppMons.size() * 150 + kills * 360) : 0;
                    if (score > 0) {
                        out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), null, null, null)));
                    }
                }
                continue;
            }

            // 8. Targeted enemy debuffs & Mini-Goule familiar sacrifice removal
            if ("SHADOW_F_10".equals(cid)) {
                List<CardInstance> fams = abilities.findTargetCandidates(room, bot, card, "OPPONENT_FAMILIAR");
                if (!fams.isEmpty() && !(preserveSingleOppFam && fams.size() == 1)) {
                    CardInstance best = fams.stream()
                            .max(Comparator.comparingInt(f -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, f)))
                            .orElseThrow();
                    int bestThreat = BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, best);
                    if (bestThreat >= 45 || difficulty == AiDifficulty.MEDIUM) {
                        out.add(new ScoredCommand(480 + bestThreat * 2, activateCmd(room, card.getInstanceId(), best.getInstanceId(), null, null)));
                    }
                }
                continue;
            }

            if (primary.target().startsWith("OPPONENT_")) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, primary.target());
                if (!cands.isEmpty()) {
                    int atkD = ("MODIFY_ATK".equals(primary.action()) || "MODIFY_ATK_AND_DEF".equals(primary.action()))
                            && primary.value() != null ? primary.value() : 0;
                    int defD = ("MODIFY_DEF".equals(primary.action()) || "MODIFY_ATK_AND_DEF".equals(primary.action()))
                            && primary.value() != null ? primary.value() : 0;
                    boolean persistsNextTurn = "END_OF_NEXT_OPPONENT_TURN".equals(primary.duration());

                    CardInstance best = pickBestEnemyDebuffTarget(room, bot, cands, atkD, defD, preserveSingleOppFam);
                    if (best != null) {
                        boolean lethalDefDrop = defD < 0 && evaluator.evaluateEffectiveDef(room, best) + defD <= 0;
                        boolean enablesKill = !room.isTurn1TruceActive() && defD < 0
                                && wouldDefDropUnlockAllyKill(room, bot, best, -defD);
                        if (lethalDefDrop || persistsNextTurn || !room.isTurn1TruceActive()) {
                            int impact = BotComboKnowledge.evaluateDebuffImpact(best, atkD, defD, evaluator, room);
                            int score = (lethalDefDrop ? 780 : 380) + impact + (enablesKill ? 240 : 0);
                            out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), best.getInstanceId(), null, null)));
                        }
                    }
                }
                continue;
            }

            // 9. Ally Monster ATK / DEF buff activations
            if ("ALLY_MONSTER".equals(primary.target()) || "ALLY_OTHER_MONSTER".equals(primary.target()) || "SELF".equals(primary.target())) {
                if ("SELF".equals(primary.target())) {
                    // Golem Obsidien +1 DEF until next opp turn
                    out.add(new ScoredCommand(310, activateCmd(room, card.getInstanceId(), null, null, null)));
                } else {
                    boolean atkBuff = "MODIFY_ATK".equals(primary.action());
                    if (atkBuff) {
                        if (!room.isTurn1TruceActive()) {
                            CardInstance bestAttacker = pickBestAllyForTurnAtkBuff(room, bot, card, primary.value() != null ? primary.value() : 1);
                            if (bestAttacker != null) {
                                int score = 440 + evaluateTurnAtkBuffGain(room, bot, bestAttacker, 1);
                                out.add(new ScoredCommand(score, activateCmd(room, card.getInstanceId(), bestAttacker.getInstanceId(), null, null)));
                            }
                        }
                    } else {
                        CardInstance allyTarget = pickBestAllyMonsterToBuffDef(room, bot, card);
                        if (allyTarget != null) {
                            out.add(new ScoredCommand(320, activateCmd(room, card.getInstanceId(), allyTarget.getInstanceId(), null, null)));
                        }
                    }
                }
            }
        }
    }

    private void evaluateHandPlayCandidates(
            GameRoom room,
            PlayerState bot,
            AiDifficulty difficulty,
            int potentialTotalEnergy,
            List<ScoredCommand> out) {
        boolean hasOwnFamiliar = !bot.getAllFamiliars().isEmpty();
        boolean hasOwnMonster = !bot.getAllMonsters().isEmpty();
        boolean hasOwnEpic = bot.hasEpicMonsterOnBoard();
        int minAffordableOnSpellCastCost = bot.findFirstFreeMonsterSlot() >= 0
                ? bot.getHand().stream()
                        .filter(c -> BotComboKnowledge.isOnSpellCastEngine(c.getDefinition())
                                && c.getDefinition().energyCost() <= bot.getEnergyTokens())
                        .mapToInt(c -> c.getDefinition().energyCost())
                        .min()
                        .orElse(Integer.MAX_VALUE)
                : Integer.MAX_VALUE;
        boolean hasPayoffNeedingFamiliar = bot.getHand().stream()
                .anyMatch(c -> BotComboKnowledge.isAnyCondition(c.getDefinition(), "ALLY_FAMILIAR_PRESENT")
                        && c.getDefinition().energyCost() <= potentialTotalEnergy)
                || bot.getAllMonsters().stream()
                .anyMatch(m -> "LIGHT_M_08".equals(m.getDefinition().cardId()) && !m.isActivatedThisTurn() && !m.isExhausted());
        boolean hasPayoffNeedingMonster = bot.getHand().stream()
                .anyMatch(c -> BotComboKnowledge.isAnyCondition(c.getDefinition(), "ALLY_MONSTER_PRESENT")
                        && c.getDefinition().energyCost() <= potentialTotalEnergy)
                || bot.getAllFamiliars().stream()
                .anyMatch(f -> BotComboKnowledge.isAnyCondition(f.getDefinition(), "ALLY_MONSTER_PRESENT")
                        && !f.isActivatedThisTurn() && !f.isExhausted());

        boolean preserveSingleOppFam = BotComboKnowledge.shouldPreserveSingleOpponentFamiliar(room, bot, evaluator);

        for (CardInstance card : bot.getHand()) {
            CardDefinition def = card.getDefinition();
            if (def.cardType() == CardType.SPELL_REACTIVE) {
                continue;
            }
            if (def.energyCost() > bot.getEnergyTokens()) {
                continue;
            }

            if (!def.cardType().isSpellCategory()) {
                if (!canPlayCardSafely(room, bot, card, null)) {
                    continue;
                }
                int score = scorePlayCreatureStrategic(
                        room, bot, card, difficulty, hasOwnFamiliar, hasOwnMonster, hasOwnEpic,
                        hasPayoffNeedingFamiliar, hasPayoffNeedingMonster, potentialTotalEnergy);
                if (score > 0) {
                    GameActionCommand cmd = buildPlayCommand(room, bot, card, difficulty);
                    out.add(new ScoredCommand(score, cmd));
                }
                continue;
            }

            // Spell card (SPELL_NORMAL, SPELL_ULTIMATE, CURSE, ARTIFACT)
            if (bot.isSpellsLockedThisTurn()) {
                continue;
            }
            Integer modalChoice = null;
            if (BotComboKnowledge.hasAbility(def, "ON_PLAY", "MODAL_CHOICE")) {
                List<AbilityOption> opts = def.abilitiesScript().stream()
                        .filter(a -> "MODAL_CHOICE".equals(a.action()) && a.options() != null)
                        .findFirst()
                        .map(AbilityDefinition::options)
                        .orElse(List.of());
                if (!opts.isEmpty()) {
                    modalChoice = pickModalOptionForCard(room, bot, card.getInstanceId(), opts, difficulty);
                }
            }
            if (!canPlayCardSafely(room, bot, card, modalChoice)) {
                continue;
            }

            boolean deferForOnSpellCast = minAffordableOnSpellCastCost < Integer.MAX_VALUE
                    && minAffordableOnSpellCastCost + def.energyCost() <= potentialTotalEnergy;
            int score = scorePlaySpellStrategic(
                    room, bot, card, modalChoice, difficulty, deferForOnSpellCast, preserveSingleOppFam);
            if (score > 0) {
                GameActionCommand cmd = buildPlaySpellCommand(room, bot, card, modalChoice, difficulty, preserveSingleOppFam);
                if (cmd != null) {
                    out.add(new ScoredCommand(score, cmd));
                }
            }
        }
    }

    private int scorePlayCreatureStrategic(
            GameRoom room,
            PlayerState bot,
            CardInstance card,
            AiDifficulty difficulty,
            boolean hasOwnFamiliar,
            boolean hasOwnMonster,
            boolean hasOwnEpic,
            boolean hasPayoffNeedingFamiliar,
            boolean hasPayoffNeedingMonster,
            int potentialTotalEnergy) {
        CardDefinition def = card.getDefinition();
        int atk = BotComboKnowledge.estimateCreaturePlayAtk(room, bot, def, evaluator);
        int effDef = BotComboKnowledge.estimateCreaturePlayDef(room, bot, def);
        int score = 200 + def.energyCost() * 55 + atk * 36 + effDef * 24;
        String cid = def.cardId();

        // Scaling & ramp engines get highest curve priority
        if ("SHADOW_M_10".equals(cid)) {
            score += 360;
        } else if ("LIGHT_M_09".equals(cid)) {
            score += 320 + bot.getAllBoardCreatures().size() * 45;
        } else if ("ICE_F_09".equals(cid)) {
            score += 330; // +1⚡ every turn start!
        } else if ("FIRE_F_09".equals(cid)) {
            boolean epicPresentOrPlayable = hasOwnEpic || bot.getHand().stream()
                    .anyMatch(h -> h.getDefinition().isEpic() && h.getDefinition().energyCost() <= potentialTotalEnergy);
            score += epicPresentOrPlayable ? 350 : 180;
        } else if (def.isEpic()) {
            boolean griffonOnBoard = bot.getAllFamiliars().stream().anyMatch(f -> "FIRE_F_09".equals(f.getDefinition().cardId()));
            score += griffonOnBoard ? 240 : 140;
        }

        // Enabler-before-payoff sequencing!
        if (def.cardType() == CardType.FAMILIAR && !hasOwnFamiliar && hasPayoffNeedingFamiliar) {
            score += 750; // Always summon Familiar before Chevalier/Yeti/Archange/Dragon Céleste!
        }
        if (def.cardType() == CardType.MONSTER && !hasOwnMonster && hasPayoffNeedingMonster) {
            score += 420;
        }

        // ON_SPELL_CAST monster before casting affordable spells on Turn 2+
        if (BotComboKnowledge.isOnSpellCastEngine(def) && !room.isTurn1TruceActive()) {
            long affordableSpells = bot.getHand().stream()
                    .filter(s -> s.getDefinition().cardType().isSpellCategory()
                            && s.getDefinition().cardType() != CardType.SPELL_REACTIVE
                            && def.energyCost() + s.getDefinition().energyCost() <= potentialTotalEnergy)
                    .count();
            if (affordableSpells > 0) {
                score += (int) (460 + affordableSpells * 120);
            }
        }

        // Penalty if condition isn't satisfied yet AND we still have an enabler affordable right now
        if (BotComboKnowledge.isAnyCondition(def, "ALLY_FAMILIAR_PRESENT") && !hasOwnFamiliar) {
            boolean affordableFamNow = bot.getHand().stream()
                    .anyMatch(f -> f.getDefinition().cardType() == CardType.FAMILIAR
                            && f.getDefinition().energyCost() + def.energyCost() <= bot.getEnergyTokens());
            score += affordableFamNow ? -900 : -110;
        }

        // Swarm synergies (ALLY_OTHER_MONSTER / ALLY_OTHER_FAMILIAR / SHADOW_M_09 + Familiar)
        if (("FIRE_M_03".equals(cid) || "SHADOW_M_01".equals(cid)) && hasOwnMonster) {
            score += 120;
        }
        if (("FIRE_F_08".equals(cid) || "LIGHT_F_06".equals(cid)) && hasOwnFamiliar) {
            score += 110;
        }
        if (def.cardType() == CardType.FAMILIAR && bot.getAllMonsters().stream().anyMatch(m -> "SHADOW_M_09".equals(m.getDefinition().cardId()))) {
            score += 140;
        }
        if ("ICE_F_04".equals(cid) || "LIGHT_F_04".equals(cid) || "FIRE_F_05".equals(cid) || "SHADOW_F_02".equals(cid)) {
            score += 115;
        }
        if (BotComboKnowledge.hasKeyword(def, "SURVOL")) {
            score += 95;
        }
        if (BotComboKnowledge.hasKeyword(def, "GARDIEN")) {
            int maxOppAtk = BotComboKnowledge.maxVisibleEnemyAttack(room, bot.getTeamId(), evaluator);
            score += maxOppAtk >= 3 ? 150 : 75;
        }

        // ON_PLAY immediate utility (Spectre de Gel, Dragonneau Ardent)
        if ("ICE_M_05".equals(cid)) {
            boolean unslowedEnemy = room.getOpponentPlayers(bot.getTeamId()).stream()
                    .flatMap(p -> p.getAllMonsters().stream())
                    .anyMatch(m -> !m.isSlowedCurrentTurn() && !evaluator.hasKeyword(room, m, "CAMOUFLAGE"));
            score += unslowedEnemy ? 210 : 30;
        } else if ("FIRE_M_04".equals(cid)) {
            boolean killable1Def = room.getOpponentPlayers(bot.getTeamId()).stream()
                    .flatMap(p -> p.getAllBoardCreatures().stream())
                    .anyMatch(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE") && evaluator.evaluateEffectiveDef(room, c) == 1);
            score += killable1Def ? 240 : (!room.isTurn1TruceActive() ? 90 : 20);
        }

        if ("FIRE_M_02".equals(cid) && BotComboKnowledge.countVisibleOpponentFamiliars(room, bot.getTeamId(), evaluator, false) > 0) {
            score += 180;
        }
        return score;
    }

    private int scorePlaySpellStrategic(
            GameRoom room,
            PlayerState bot,
            CardInstance card,
            Integer modalChoice,
            AiDifficulty difficulty,
            boolean deferForOnSpellCast,
            boolean preserveSingleOppFam) {
        CardDefinition def = card.getDefinition();
        String cid = def.cardId();
        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        int maxLp = room.getTeam(bot.getTeamId()).getMaxLp();
        int oppLp = room.getTeam(bot.getTeamId().opponent()).getLp();
        int oppRed = evaluator.evaluateTeamLpDamageReduction(room, bot.getTeamId().opponent());

        // Defer non-urgent spell casts if bot is holding an affordable ON_SPELL_CAST engine on Turn 2+!
        int deferSpellCast = (difficulty == AiDifficulty.HARD && deferForOnSpellCast && !room.isTurn1TruceActive()
                && !"LIGHT_M_04".equals(cid) && !"ICE_M_03".equals(cid)) ? -350 : 0;

        if (def.cardType() == CardType.CURSE) {
            if (BotComboKnowledge.isDuplicateActiveCurse(bot, cid)) {
                return 0;
            }
            int oppMonCount = BotComboKnowledge.countVisibleOpponentMonsters(room, bot.getTeamId(), evaluator, false);
            if (oppMonCount == 0 && difficulty == AiDifficulty.HARD && bot.getHand().size() < PlayerState.MAX_HAND_SIZE_END_TURN) {
                return 0;
            }
            return 280 + oppMonCount * 165 + deferSpellCast;
        }

        if (def.cardType() == CardType.ARTIFACT) {
            if (BotComboKnowledge.isDuplicateActiveCurse(bot, cid)) {
                return 0;
            }
            int myMonCount = bot.getAllMonsters().size();
            if (myMonCount == 0 && difficulty == AiDifficulty.HARD) {
                return 0;
            }
            return 220 + myMonCount * 155 + deferSpellCast;
        }

        // Single-turn buffs & heals discipline
        if ("LIGHT_S_02".equals(cid)) { // Soin Arcanique (+3 LP)
            if (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot)) {
                return 620;
            }
            if (teamLp >= maxLp) {
                return 0;
            }
            return (teamLp <= 12 ? 640 : 280) + deferSpellCast;
        }
        if ("FIRE_S_02".equals(cid)) { // Feu de Camp (+2 LP + Draw 1)
            int base = (teamLp < maxLp ? 490 : 340) + (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot) ? 320 : 0);
            return base + deferSpellCast;
        }
        if ("LIGHT_S_01".equals(cid)) { // Malédiction Purificatrice (Cleanse + +1⚡)
            boolean hasDebuffedAlly = bot.getAllMonsters().stream().anyMatch(this::isCreatureDebuffedOrSlowed);
            boolean hasSpellCastOnBoard = bot.getAllMonsters().stream()
                    .anyMatch(m -> BotComboKnowledge.isOnSpellCastEngine(m.getDefinition())
                            && evaluator.canCreatureAttackNow(room, m));
            if (!hasDebuffedAlly && !hasSpellCastOnBoard && difficulty == AiDifficulty.HARD
                    && bot.getHand().size() < PlayerState.MAX_HAND_SIZE_END_TURN) {
                return 0; // Hold cleanse for when an ally actually gets slowed or debuffed!
            }
            return (hasDebuffedAlly ? 720 : 360) + (hasSpellCastOnBoard ? 240 : 0);
        }

        if ("FIRE_S_01".equals(cid)) { // Chaleur Arcanique (+1 ATQ + +1⚡)
            if (room.isTurn1TruceActive()) {
                return (difficulty == AiDifficulty.HARD && bot.getHand().size() < PlayerState.MAX_HAND_SIZE_END_TURN) ? 0 : 120;
            }
            CardInstance target = pickBestAllyForTurnAtkBuff(room, bot, null, 1);
            if (target == null) {
                return 0;
            }
            return 520 + evaluateTurnAtkBuffGain(room, bot, target, 1);
        }
        if ("ICE_S_01".equals(cid)) { // Givre Protecteur (+2 DEF until next opp turn + +1⚡)
            CardInstance mon = pickBestAllyMonsterToBuffDef(room, bot, null);
            return mon != null ? 430 + (BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, mon) / 2) : 0;
        }
        if ("LIGHT_S_06".equals(cid)) { // Bénédiction Arcanique (all ally monsters +1/+1 END_OF_TURN)
            if (room.isTurn1TruceActive()) {
                return 0;
            }
            long readyCount = bot.getAllMonsters().stream().filter(m -> evaluator.canCreatureAttackNow(room, m)).count();
            if (readyCount == 0) {
                return 0;
            }
            return (int) (260 + readyCount * 170) + deferSpellCast;
        }

        // Targeted / AoE enemy spells
        if ("FIRE_S_03".equals(cid)) {
            if (Integer.valueOf(1).equals(modalChoice)) {
                int dmg = Math.max(0, 2 - oppRed);
                return (oppLp <= dmg ? 1600 : 310) + deferSpellCast;
            }
            List<CardInstance> targets = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER_DEF_LE_3")
                    .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList();
            if (targets.isEmpty()) {
                return 0;
            }
            int bestThreat = targets.stream()
                    .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                    .max().orElse(0);
            return 540 + bestThreat * 3 + deferSpellCast;
        }

        if ("LIGHT_S_08".equals(cid)) {
            if (Integer.valueOf(0).equals(modalChoice)) {
                List<CardInstance> targets = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER_DEF_LE_3")
                        .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList();
                if (targets.isEmpty()) {
                    return 0;
                }
                int bestThreat = targets.stream()
                        .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                        .max().orElse(0);
                return 520 + bestThreat * 3 + deferSpellCast;
            }
            List<CardInstance> mons = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER");
            if (mons.isEmpty()) {
                return 0;
            }
            int kills = (int) mons.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= 3).count();
            return (room.isTurn1TruceActive() && kills == 0) ? 0 : (460 + kills * 350 + deferSpellCast);
        }

        if ("SHADOW_S_07".equals(cid)) { // Main de l'Ombre (destroy opp monster ATK <= 2)
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER_ATK_LE_2")
                    .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList();
            if (cands.isEmpty()) {
                return 0;
            }
            int bestThreat = cands.stream()
                    .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                    .max().orElse(0);
            return 480 + bestThreat * 3 + deferSpellCast;
        }

        if ("FIRE_S_06".equals(cid) || "ICE_S_04".equals(cid) || "SHADOW_S_04".equals(cid) || "SHADOW_S_03".equals(cid)) {
            List<CardInstance> fams = abilities.findTargetCandidates(room, bot, card, "OPPONENT_FAMILIAR")
                    .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList();
            if (fams.isEmpty() || (preserveSingleOppFam && fams.size() == 1)) {
                return 0;
            }
            int bestThreat = fams.stream()
                    .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                    .max().orElse(0);
            int bonus = "SHADOW_S_03".equals(cid) ? 140 : 0; // refunds +1⚡
            return (bestThreat >= 42 || fams.size() >= 2 || bonus > 0 ? 370 + bestThreat * 3 + bonus : 150) + deferSpellCast;
        }

        if ("ICE_S_02".equals(cid)) { // Chaînes de Glace (APPLY_SLOW)
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER")
                    .stream().filter(m -> !m.isSlowedCurrentTurn() && evaluator.evaluateEffectiveAtk(room, m) >= 2).toList();
            if (cands.isEmpty()) {
                return 0;
            }
            int maxThreat = cands.stream()
                    .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                    .max().orElse(0);
            return 340 + maxThreat * 3 + deferSpellCast;
        }

        if ("ICE_S_05".equals(cid)) { // Frimas (-2 ATQ until end of next opp turn)
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER")
                    .stream().filter(m -> !m.isSlowedCurrentTurn() && evaluator.evaluateEffectiveAtk(room, m) >= 2).toList();
            if (cands.isEmpty()) {
                return 0;
            }
            int maxThreat = cands.stream()
                    .mapToInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t))
                    .max().orElse(0);
            return 320 + maxThreat * 2 + deferSpellCast;
        }

        if ("SHADOW_S_05".equals(cid)) { // Siphon de Vie (-2 DEF or 2 LP if empty, +2 LP)
            int oppMons = BotComboKnowledge.countVisibleOpponentMonsters(room, bot.getTeamId(), evaluator, true);
            if (oppMons == 0) {
                return 520 + (teamLp < maxLp ? 120 : 0) + deferSpellCast;
            }
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER");
            int kills = (int) cands.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= 2).count();
            if (kills > 0) {
                return 620 + kills * 220 + deferSpellCast;
            }
            return !room.isTurn1TruceActive() ? 390 + (teamLp < maxLp ? 110 : 0) + deferSpellCast : 0;
        }

        if ("SHADOW_S_10".equals(cid)) { // Nuit Éternelle (Lock opp spells next turn)
            boolean alreadyLocked = room.getOpponentPlayers(bot.getTeamId()).stream().anyMatch(PlayerState::isSpellsLockedNextTurn);
            if (alreadyLocked) {
                return 0;
            }
            return (room.getTurnNumber() >= 3 ? 490 : 280) + deferSpellCast;
        }

        if ("ICE_S_10".equals(cid)) { // Tempête Hivernale (-2 DEF all opp monsters + FORCE_DEFENSE)
            List<CardInstance> oppMons = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_MONSTERS", false, AbilityContext.empty());
            if (oppMons.isEmpty()) {
                return 0;
            }
            int kills = (int) oppMons.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= 2).count();
            int atkFlipped = (int) oppMons.stream().filter(m -> m.getPosture() == Posture.ATTACK && evaluator.evaluateEffectiveAtk(room, m) > 2).count();
            if (room.isTurn1TruceActive() && kills == 0) {
                return 0;
            }
            return 600 + kills * 380 + atkFlipped * 120 + oppMons.size() * 90 + deferSpellCast;
        }

        if ("FIRE_S_10".equals(cid)) { // Éruption (DESTROY_ALL opp familiars + -1 DEF all opp monsters)
            List<CardInstance> oppFams = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_FAMILIARS", false, AbilityContext.empty())
                    .stream().filter(f -> !evaluator.hasSpellDestructionImmunity(room, f)).toList();
            List<CardInstance> oppMons = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_MONSTERS", false, AbilityContext.empty());
            int totalAffected = oppFams.size() + oppMons.size();
            if (totalAffected == 0 || (preserveSingleOppFam && oppFams.size() == 1 && oppMons.isEmpty())) {
                return 0;
            }
            int monKills = (int) oppMons.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= 1).count();
            return 380 + oppFams.size() * 210 + monKills * 350 + oppMons.size() * 75 + deferSpellCast;
        }

        if ("FIRE_S_09".equals(cid) || "LIGHT_S_10".equals(cid) || "ICE_S_09".equals(cid)
                || "LIGHT_S_07".equals(cid) || "LIGHT_S_09".equals(cid)) {
            List<CardInstance> oppMons = abilities.resolveTargets(room, bot, card, "ALL_OPPONENT_MONSTERS", false, AbilityContext.empty());
            if (oppMons.isEmpty()) {
                return 0;
            }
            int defDrop = ("FIRE_S_09".equals(cid) || "LIGHT_S_10".equals(cid) || "LIGHT_S_09".equals(cid)) ? 2 : 1;
            int kills = (int) oppMons.stream().filter(m -> evaluator.evaluateEffectiveDef(room, m) <= defDrop).count();
            if (room.isTurn1TruceActive() && kills == 0) {
                return 0;
            }
            return 350 + kills * 360 + oppMons.size() * 110 + deferSpellCast;
        }

        if ("LIGHT_S_03".equals(cid) || "FIRE_S_07".equals(cid) || "SHADOW_S_08".equals(cid)) {
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER");
            if (cands.isEmpty()) {
                return 0;
            }
            int atkD = "LIGHT_S_03".equals(cid) ? 0 : -2;
            CardInstance best = pickBestEnemyDebuffTarget(room, bot, cands, atkD, -2, false);
            if (best == null) {
                return 0;
            }
            boolean kills = evaluator.evaluateEffectiveDef(room, best) <= 2;
            boolean enablesKill = !room.isTurn1TruceActive() && wouldDefDropUnlockAllyKill(room, bot, best, 2);
            if (room.isTurn1TruceActive() && !kills) {
                return 0;
            }
            return (kills ? 650 : (enablesKill ? 480 : 280))
                    + BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, best) * 2
                    + deferSpellCast;
        }

        return Math.max(0, 220 + def.energyCost() * 35 + deferSpellCast);
    }

    private void evaluateReactiveSetCandidates(
            GameRoom room, PlayerState bot, AiDifficulty difficulty, List<ScoredCommand> out) {
        if (bot.isSpellsLockedThisTurn()) {
            return;
        }
        int freeSlot = bot.findFirstFreeSpellSlot();
        if (freeSlot < 0) {
            return;
        }
        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        int maxOppAtk = BotComboKnowledge.maxVisibleEnemyAttack(room, bot.getTeamId(), evaluator);
        boolean threatened = teamLp <= 14 || maxOppAtk >= 3 || !bot.getAllMonsters().isEmpty();

        for (CardInstance card : bot.getHand()) {
            if (card.getDefinition().cardType() != CardType.SPELL_REACTIVE
                    || card.getDefinition().energyCost() > bot.getEnergyTokens()) {
                continue;
            }
            boolean duplicateFaceDown = bot.getAllSpellArtifacts().stream()
                    .anyMatch(s -> s.isFaceDown() && s.getDefinition().cardId().equals(card.getDefinition().cardId()));
            if (duplicateFaceDown && difficulty == AiDifficulty.HARD) {
                continue;
            }
            int base = threatened ? 145 : 70;
            if ("SHADOW_S_01".equals(card.getDefinition().cardId()) && !bot.getAllMonsters().isEmpty()) {
                base += 80; // Cheap 1-cost protection for high-value monster
            }
            if (teamLp <= 10) {
                base += 130;
            }
            out.add(new ScoredCommand(base, new GameActionCommand(
                    room.getMatchId(), null, GameActionType.SET_REACTIVE_SPELL,
                    card.getInstanceId(), freeSlot, null, null, null, null, null)));
        }
    }

    private void evaluatePostureSwitchCandidates(
            GameRoom room, PlayerState bot, AiDifficulty difficulty, List<ScoredCommand> out) {
        if (room.isTurn1TruceActive()) {
            return;
        }
        int maxOppAtk = BotComboKnowledge.maxVisibleEnemyAttack(room, bot.getTeamId(), evaluator);
        int maxOppWallDef = room.getOpponentPlayers(bot.getTeamId()).stream()
                .flatMap(p -> p.getAllMonsters().stream())
                .filter(m -> !evaluator.hasKeyword(room, m, "CAMOUFLAGE"))
                .mapToInt(m -> m.getPosture() == Posture.DEFENSE
                        ? evaluator.evaluateEffectiveDef(room, m)
                        : evaluator.evaluateEffectiveAtk(room, m))
                .max()
                .orElse(0);
        int oppMonCount = BotComboKnowledge.countVisibleOpponentMonsters(room, bot.getTeamId(), evaluator, true);

        for (CardInstance monster : bot.getAllMonsters()) {
            if (monster.isChangedPostureThisTurn() || monster.isSlowedCurrentTurn()) {
                continue;
            }
            int myAtk = evaluator.evaluateEffectiveAtk(room, monster);
            int myDef = evaluator.evaluateEffectiveDef(room, monster);
            boolean hasSurvol = evaluator.hasKeyword(room, monster, "SURVOL");
            boolean oppHasNonCamoFam = BotComboKnowledge.countVisibleOpponentFamiliars(room, bot.getTeamId(), evaluator, true) > 0;

            if (monster.getPosture() == Posture.DEFENSE) {
                // Keep Golem de Glace in DEFENSE unless safe lethal or critical value kill!
                if ("ICE_M_09".equals(monster.getDefinition().cardId())) {
                    continue;
                }
                boolean hasProfitableTarget = (oppMonCount == 0 && (!oppHasNonCamoFam || hasSurvol))
                        || hasSafeCombatKillTarget(room, bot, monster, myAtk);
                boolean safeCrackback = maxOppAtk < myAtk || myAtk >= myDef;
                if (hasProfitableTarget && safeCrackback) {
                    out.add(new ScoredCommand(195 + myAtk * 15, new GameActionCommand(
                            room.getMatchId(), null, GameActionType.CHANGE_POSTURE,
                            monster.getInstanceId(), null, Posture.ATTACK, null, null, null, null)));
                }
            } else if (difficulty == AiDifficulty.HARD) {
                // Guard high-DEF scaling monster (Hydre/Golem/Light Golem) in DEFENSE ONLY if it has NO favorable attack AND survives better in DEFENSE
                boolean canAttackWell = (oppMonCount == 0 && (!oppHasNonCamoFam || hasSurvol))
                        || hasSafeCombatKillTarget(room, bot, monster, myAtk);
                if (!canAttackWell
                        && monster.getAttacksMadeThisTurn() == 0
                        && ("ICE_M_09".equals(monster.getDefinition().cardId())
                                || (myDef > myAtk && maxOppAtk >= myAtk && maxOppAtk <= myDef && maxOppWallDef >= myAtk))) {
                    out.add(new ScoredCommand(130 + myDef * 10, new GameActionCommand(
                            room.getMatchId(), null, GameActionType.CHANGE_POSTURE,
                            monster.getInstanceId(), null, Posture.DEFENSE, null, null, null, null)));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Optimal Multi-Attacker Combat Pairing (MEDIUM & HARD)
    // -------------------------------------------------------------------------

    private Optional<GameActionCommand> chooseBestCombatAttack(
            GameRoom room, PlayerState bot, AiDifficulty difficulty) {
        List<CardInstance> ready = bot.getAllBoardCreatures().stream()
                .filter(c -> evaluator.canCreatureAttackNow(room, c))
                .toList();
        if (ready.isEmpty()) {
            return Optional.empty();
        }

        TeamId oppTeam = bot.getTeamId().opponent();
        List<CardInstance> nonCamoGuardians = new ArrayList<>();
        List<CardInstance> nonCamoMonsters = new ArrayList<>();
        List<CardInstance> nonCamoFamiliars = new ArrayList<>();

        for (PlayerState opp : room.getTeamPlayers(oppTeam)) {
            for (CardInstance c : opp.getAllBoardCreatures()) {
                if (evaluator.hasKeyword(room, c, "CAMOUFLAGE")) {
                    continue;
                }
                if (evaluator.hasKeyword(room, c, "GARDIEN")) {
                    nonCamoGuardians.add(c);
                }
                if (c.getDefinition().cardType() == CardType.MONSTER) {
                    nonCamoMonsters.add(c);
                } else {
                    nonCamoFamiliars.add(c);
                }
            }
        }

        boolean preserveSingleOppFam = difficulty == AiDifficulty.HARD
                && BotComboKnowledge.shouldPreserveSingleOpponentFamiliar(room, bot, evaluator);

        // If enemy ONLY has familiars (no monsters/gardiens), compare clearing familiars first vs SURVOL face push
        boolean oppOnlyFamiliars = nonCamoGuardians.isEmpty() && nonCamoMonsters.isEmpty() && !nonCamoFamiliars.isEmpty();
        int nonSurvolReadyAtk = oppOnlyFamiliars
                ? ready.stream().filter(a -> !evaluator.hasKeyword(room, a, "SURVOL"))
                        .mapToInt(a -> evaluator.evaluateEffectiveAtk(room, a)).sum()
                : 0;

        GameActionCommand bestCmd = null;
        int bestScore = 0;

        for (CardInstance attacker : ready) {
            boolean hasSurvol = evaluator.hasKeyword(room, attacker, "SURVOL");
            int allyVal = BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, attacker);
            int myAtk = evaluator.evaluateEffectiveAtk(room, attacker);

            // Option A: Direct Attack
            boolean directLegal = nonCamoGuardians.isEmpty()
                    && nonCamoMonsters.isEmpty()
                    && (nonCamoFamiliars.isEmpty() || hasSurvol);
            if (directLegal) {
                int bonus = hasSurvol && oppOnlyFamiliars && nonSurvolReadyAtk > myAtk && nonCamoFamiliars.size() == 1
                        ? -180 // Let non-SURVOL clearer or Dragon de Braise keep/clear properly, or wait if SURVOL hits 3 vs 5 behind it!
                        : 60;
                int directScore = 360 + myAtk * 45 + bonus;
                if ("ICE_M_02".equals(attacker.getDefinition().cardId()) && !nonCamoFamiliars.isEmpty()) {
                    directScore += 45;
                }
                if ("LIGHT_M_10".equals(attacker.getDefinition().cardId()) && !bot.getAllFamiliars().isEmpty()) {
                    directScore += 45;
                }
                if ("FIRE_M_02".equals(attacker.getDefinition().cardId()) && !nonCamoFamiliars.isEmpty()) {
                    directScore += 80; // Preserve enemy familiar during multi-attack!
                }
                if (directScore > bestScore) {
                    bestScore = directScore;
                    Integer choiceOpt = "FIRE_M_10".equals(attacker.getDefinition().cardId()) && !nonCamoFamiliars.isEmpty()
                            ? 0 : null;
                    bestCmd = new GameActionCommand(
                            room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                            attacker.getInstanceId(), null, null, CombatResolver.DIRECT_PLAYER_TARGET, null, choiceOpt, null);
                }
            }

            // Option B: Creature Combat
            List<CardInstance> legalTargets;
            if (!nonCamoGuardians.isEmpty()) {
                legalTargets = nonCamoGuardians;
            } else {
                legalTargets = new ArrayList<>(nonCamoMonsters.size() + nonCamoFamiliars.size());
                legalTargets.addAll(nonCamoMonsters);
                legalTargets.addAll(nonCamoFamiliars);
            }

            for (CardInstance target : legalTargets) {
                boolean isDragonBraise = "FIRE_M_10".equals(attacker.getDefinition().cardId())
                        && BotComboKnowledge.countVisibleOpponentFamiliars(room, bot.getTeamId(), evaluator, false) > 0;
                int effectiveAtk = myAtk + (isDragonBraise ? 2 : 0);
                int oppAtk = evaluator.evaluateEffectiveAtk(room, target);
                int oppDef = evaluator.evaluateEffectiveDef(room, target);
                int threat = BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, target);
                boolean targetIsMonster = target.getDefinition().cardType() == CardType.MONSTER;
                boolean targetIsFam = !targetIsMonster;

                int score;
                Integer modalChoice = isDragonBraise ? 0 : null;

                if (target.getPosture() == Posture.ATTACK) {
                    if (effectiveAtk > oppAtk) {
                        int excess = effectiveAtk - oppAtk;
                        // Prefer smallest clean killer (lower myAtk wins ties so bigger cannons hit harder targets or face!)
                        score = 480 + threat * 5 + excess * 30 - myAtk * 12;
                    } else if (effectiveAtk == oppAtk) {
                        int tradeDelta = threat - allyVal;
                        if (tradeDelta >= 15 || evaluator.hasKeyword(room, target, "GARDIEN")) {
                            score = 180 + tradeDelta * 4;
                        } else {
                            score = -200;
                        }
                    } else {
                        score = -9999; // Losing combat + recoil damage
                    }
                } else {
                    if (effectiveAtk > oppDef) {
                        score = 440 + threat * 5 - myAtk * 10;
                    } else if (effectiveAtk == oppDef && difficulty == AiDifficulty.HARD
                            && !"ICE_F_08".equals(target.getDefinition().cardId())
                            && ("FIRE_M_01".equals(attacker.getDefinition().cardId())
                                    || "ICE_M_02".equals(attacker.getDefinition().cardId())
                                    || "LIGHT_M_10".equals(attacker.getDefinition().cardId())
                                    || ("SHADOW_M_07".equals(attacker.getDefinition().cardId()) && targetIsMonster))) {
                        // Zero-recoil safe poke that still triggers ON_ATTACK / ON_ATTACK_SURVIVE LP ping or 1 DEF familiar kill!
                        score = 95;
                        if (isDragonBraise) {
                            modalChoice = 1; // Deal 2 direct LP when bouncing off DEF 7 wall
                            score = 150;
                        }
                    } else {
                        score = -9999;
                    }
                }

                if (score <= 0) {
                    continue;
                }

                // Special trigger & hazard adjustments
                if ("SHADOW_M_07".equals(attacker.getDefinition().cardId()) && targetIsMonster
                        && nonCamoFamiliars.stream().anyMatch(f -> evaluator.evaluateEffectiveDef(room, f) == 1)) {
                    score += 180;
                }
                if ("SHADOW_M_05".equals(attacker.getDefinition().cardId()) && targetIsFam) {
                    score += 190;
                }
                if (targetIsFam && nonCamoFamiliars.size() == 1 && preserveSingleOppFam
                        && !"SHADOW_M_05".equals(attacker.getDefinition().cardId())) {
                    score -= 160;
                }
                if (targetIsFam && oppOnlyFamiliars && nonSurvolReadyAtk > myAtk && !hasSurvol) {
                    score += 140; // Clear last Rempart familiar so non-SURVOL carry can swing face!
                }
                if ("ICE_F_08".equals(target.getDefinition().cardId())) {
                    boolean isFrenesieFirst = "FIRE_M_02".equals(attacker.getDefinition().cardId())
                            && attacker.getAttacksMadeThisTurn() == 0;
                    score -= isFrenesieFirst ? 400 : (140 + allyVal / 2);
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestCmd = new GameActionCommand(
                            room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                            attacker.getInstanceId(), null, null, target.getInstanceId(), null, modalChoice, null);
                }
            }
        }

        return Optional.ofNullable(bestCmd);
    }

    private Optional<GameActionCommand> chooseBasicAttack(GameRoom room, PlayerState bot) {
        TeamId oppTeam = bot.getTeamId().opponent();
        List<CardInstance> monsters = room.getTeamPlayers(oppTeam).stream()
                .flatMap(p -> p.getAllMonsters().stream())
                .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                .toList();
        List<CardInstance> familiars = room.getTeamPlayers(oppTeam).stream()
                .flatMap(p -> p.getAllFamiliars().stream())
                .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                .toList();
        List<CardInstance> guardians = room.getTeamPlayers(oppTeam).stream()
                .flatMap(p -> p.getAllBoardCreatures().stream())
                .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE") && evaluator.hasKeyword(room, c, "GARDIEN"))
                .toList();

        for (CardInstance atk : bot.getAllBoardCreatures()) {
            if (!evaluator.canCreatureAttackNow(room, atk)) {
                continue;
            }
            boolean survol = evaluator.hasKeyword(room, atk, "SURVOL");
            if (guardians.isEmpty() && monsters.isEmpty() && (familiars.isEmpty() || survol)) {
                return Optional.of(new GameActionCommand(
                        room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                        atk.getInstanceId(), null, null, CombatResolver.DIRECT_PLAYER_TARGET, null, null, null));
            }
            List<CardInstance> pool = !guardians.isEmpty() ? guardians : (!monsters.isEmpty() ? monsters : familiars);
            int myAtk = evaluator.evaluateEffectiveAtk(room, atk);
            for (CardInstance tgt : pool) {
                int oppStat = tgt.getPosture() == Posture.ATTACK
                        ? evaluator.evaluateEffectiveAtk(room, tgt)
                        : evaluator.evaluateEffectiveDef(room, tgt);
                if (myAtk >= oppStat) {
                    return Optional.of(new GameActionCommand(
                            room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                            atk.getInstanceId(), null, null, tgt.getInstanceId(), null, null, null));
                }
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Modal Choice, Reaction Economy & Helper Selectors
    // -------------------------------------------------------------------------

    private int pickModalOptionForCard(
            GameRoom room, PlayerState bot, String sourceCardInstanceId, List<AbilityOption> options, AiDifficulty difficulty) {
        if (options.size() < 2) {
            return 0;
        }
        CardInstance source = bot.findInHand(sourceCardInstanceId)
                .or(() -> bot.findOnBoard(sourceCardInstanceId))
                .orElse(null);
        if (source == null) {
            return 0;
        }
        String cid = source.getDefinition().cardId();
        int oppLp = room.getTeam(bot.getTeamId().opponent()).getLp();
        int oppRed = evaluator.evaluateTeamLpDamageReduction(room, bot.getTeamId().opponent());

        if ("FIRE_S_03".equals(cid)) { // 0: Destroy Monster <= 3 DEF | 1: 2 LP burn
            boolean hasDestroyTarget = !abilities.findTargetCandidates(room, bot, source, "OPPONENT_MONSTER_DEF_LE_3")
                    .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList().isEmpty();
            if (!hasDestroyTarget) {
                return 1;
            }
            if (difficulty == AiDifficulty.HARD && oppLp <= Math.max(0, 2 - oppRed)) {
                return 1;
            }
            return 0;
        }

        if ("LIGHT_S_08".equals(cid)) { // 0: Destroy Monster <= 3 DEF | 1: -3 DEF
            boolean hasDestroyTarget = !abilities.findTargetCandidates(room, bot, source, "OPPONENT_MONSTER_DEF_LE_3")
                    .stream().filter(t -> !evaluator.hasSpellDestructionImmunity(room, t)).toList().isEmpty();
            return hasDestroyTarget ? 0 : 1;
        }

        if ("FIRE_F_07".equals(cid)) { // 0: 1 LP burn | 1: -1 ATK end of turn
            if (difficulty == AiDifficulty.HARD && !room.isTurn1TruceActive()
                    && oppLp > Math.max(0, 1 - oppRed)
                    && findEnemyAtkDebuffFlippingCombatTrade(room, bot, 1) != null) {
                return 1;
            }
            return 0;
        }

        if ("SHADOW_F_03".equals(cid)) { // 0: -1 LP -> +1⚡ | 1: +1 LP
            int teamLp = room.getTeam(bot.getTeamId()).getLp();
            if (evaluateEnergyUnlockDemand(room, bot, 1, difficulty) > 0 && teamLp > 6) {
                return 0;
            }
            return 1;
        }

        if ("FIRE_M_10".equals(cid)) { // 0: +2 ATK | 1: 2 LP
            if (oppLp <= Math.max(0, 2 - oppRed)) {
                return 1;
            }
            return 0;
        }

        if ("LIGHT_S_04".equals(cid) && room.getPendingReaction() != null) {
            return room.getPendingReaction().isDirectAttack() ? 1 : 0;
        }

        if ("SHADOW_S_01".equals(cid)) {
            return 0; // GRANT_CAMOUFLAGE cancels incoming attack or targeted spell!
        }

        return 0;
    }

    private ReactionEval evaluateReactionOption(
            GameRoom room, PlayerState bot, PendingReactionWindow rw, CardInstance reactiveCard) {
        String cid = reactiveCard.getDefinition().cardId();
        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        int ownRed = evaluator.evaluateTeamLpDamageReduction(room, bot.getTeamId());

        if (rw.isDirectAttack()) {
            CardInstance attacker = rw.getAttackerInstanceId() != null
                    ? room.findCreatureOnAnyBoard(rw.getAttackerInstanceId()).orElse(null)
                    : null;
            int rawAtk = attacker != null ? evaluator.evaluateEffectiveAtk(room, attacker) : 1;
            int incomingDmg = Math.max(0, rawAtk - ownRed);
            if (incomingDmg == 0) {
                return ReactionEval.PASS;
            }
            boolean isLethal = teamLp <= incomingDmg;
            if ("FIRE_S_04".equals(cid) || "ICE_S_06".equals(cid)) {
                int otherReadyOppAttackers = attacker != null ? countOtherReadyAttackers(room, bot.getTeamId().opponent(), attacker.getInstanceId()) : 0;
                if (isLethal || teamLp <= 10 || incomingDmg >= 3 || otherReadyOppAttackers > 0) {
                    int bonus = otherReadyOppAttackers * 120 + (isLethal ? 900 : 0);
                    return new ReactionEval(500 + incomingDmg * 60 + bonus, null, null);
                }
                return ReactionEval.PASS;
            }
            if ("LIGHT_S_04".equals(cid)) {
                if (isLethal || teamLp <= 10 || incomingDmg >= 3) {
                    return new ReactionEval(450 + incomingDmg * 60 + (isLethal ? 900 : 0), 1, null);
                }
                return ReactionEval.PASS;
            }
            if ("FIRE_S_05".equals(cid) || "SHADOW_S_02".equals(cid)) {
                if (isLethal || teamLp <= 12 || incomingDmg >= 3) {
                    return new ReactionEval(460 + incomingDmg * 60 + (isLethal ? 900 : 0), null, null);
                }
                return ReactionEval.PASS;
            }
        }

        if (rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED && !rw.isDirectAttack()) {
            CardInstance defender = rw.getDefenderTargetId() != null
                    ? room.findCreatureOnAnyBoard(rw.getDefenderTargetId()).orElse(null)
                    : null;
            CardInstance attacker = rw.getAttackerInstanceId() != null
                    ? room.findCreatureOnAnyBoard(rw.getAttackerInstanceId()).orElse(null)
                    : null;
            boolean defDies = defender != null && attacker != null
                    && combatResolver.wouldDestroyAllyMonsterInCombat(room, attacker, defender);
            int defVal = defender != null ? BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, defender) : 50;

            if ("LIGHT_F_09".equals(cid)) { // Mini-Garde saves ally & grants +1⚡!
                return defDies ? new ReactionEval(780 + defVal, null, null) : ReactionEval.PASS;
            }
            if ("ICE_F_07".equals(cid)) { // Loutre Arctique redirects attack to self (+1 DEF combat)
                if (defDies && defVal >= 55) {
                    return new ReactionEval(640 + defVal, null, null);
                }
                return ReactionEval.PASS;
            }
            if ("LIGHT_S_04".equals(cid)) {
                if (!defDies) {
                    return ReactionEval.PASS;
                }
                boolean tradeKillsAttackerToo = attacker != null && defender != null
                        && defender.getPosture() == Posture.ATTACK
                        && evaluator.evaluateEffectiveAtk(room, defender) >= evaluator.evaluateEffectiveAtk(room, attacker);
                return new ReactionEval(600 + defVal, tradeKillsAttackerToo ? 0 : 1, null);
            }
            if ("SHADOW_S_01".equals(cid)) {
                return defDies || defVal >= 50 ? new ReactionEval(680 + defVal, 0, null) : ReactionEval.PASS;
            }
            if ("SHADOW_S_02".equals(cid) || "FIRE_S_05".equals(cid)) {
                return defDies && defVal >= 55 ? new ReactionEval(590 + defVal, null, null) : ReactionEval.PASS;
            }
        }

        if (rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.SPELL_TARGETED_ALLY_MONSTER) {
            CardInstance targeted = rw.getPendingTargetId() != null
                    ? room.findCreatureOnAnyBoard(rw.getPendingTargetId()).orElse(null)
                    : null;
            if (targeted == null) {
                return ReactionEval.PASS;
            }
            int allyVal = BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, targeted);
            if ("SHADOW_S_01".equals(cid)) {
                return new ReactionEval(650 + allyVal, 0, null);
            }
        }

        if (rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.FAMILIAR_EFFECT_ACTIVATED) {
            CardInstance fam = rw.getSourceCardInstanceId() != null
                    ? room.findCreatureOnAnyBoard(rw.getSourceCardInstanceId()).orElse(null)
                    : null;
            if (fam != null) {
                int threat = BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, fam);
                if (threat >= 45 || "FIRE_F_05".equals(fam.getDefinition().cardId())
                        || "ICE_F_09".equals(fam.getDefinition().cardId())
                        || "FIRE_F_10".equals(fam.getDefinition().cardId())
                        || "SHADOW_F_10".equals(fam.getDefinition().cardId())) {
                    return new ReactionEval(620 + threat * 2, null, null);
                }
            }
            return ReactionEval.PASS;
        }

        return ReactionEval.PASS;
    }

    private GameActionCommand pickSimpleReaction(
            GameRoom room, PlayerState bot, PendingReactionWindow rw, List<String> eligible, AiDifficulty difficulty) {
        if (difficulty == AiDifficulty.EASY && room.getTeam(bot.getTeamId()).getLp() > 10) {
            return passReaction(room);
        }
        String picked = eligible.getFirst();
        CardInstance card = bot.findOnBoard(picked).orElse(null);
        Integer choice = null;
        if (card != null && "LIGHT_S_04".equals(card.getDefinition().cardId())) {
            choice = rw.isDirectAttack() ? 1 : 0;
        } else if (card != null && "SHADOW_S_01".equals(card.getDefinition().cardId())) {
            choice = 0;
        }
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.TRIGGER_REACTION,
                picked, null, null, null, null, choice, null);
    }

    // -------------------------------------------------------------------------
    // Shared Strategic Helpers
    // -------------------------------------------------------------------------

    private boolean canPlayCardSafely(GameRoom room, PlayerState bot, CardInstance card, Integer choiceIndex) {
        CardDefinition def = card.getDefinition();
        if (def.energyCost() > bot.getEnergyTokens()) {
            return false;
        }
        return switch (def.cardType()) {
            case MONSTER -> bot.findFirstFreeMonsterSlot() >= 0 && (!def.isEpic() || !bot.hasEpicMonsterOnBoard());
            case FAMILIAR -> bot.findFirstFreeFamiliarSlot() >= 0;
            case CURSE, ARTIFACT -> !bot.isSpellsLockedThisTurn() && bot.findFirstFreeSpellSlot() >= 0;
            case SPELL_NORMAL, SPELL_ULTIMATE -> !bot.isSpellsLockedThisTurn()
                    && abilities.hasValidTargetForSpellPlay(room, bot, card, choiceIndex);
            case SPELL_REACTIVE -> false;
        };
    }

    private GameActionCommand buildPlayCommand(GameRoom room, PlayerState bot, CardInstance card, AiDifficulty difficulty) {
        CardDefinition def = card.getDefinition();
        if (def.cardType() == CardType.MONSTER) {
            int slot = bot.findFirstFreeMonsterSlot();
            Posture posture = shouldSummonInDefense(room, bot, card, difficulty) ? Posture.DEFENSE : Posture.ATTACK;
            String targetId = null;
            if ("FIRE_M_04".equals(def.cardId())) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_CREATURE");
                if (!cands.isEmpty()) {
                    targetId = pickBestEnemyDebuffTarget(room, bot, cands, 0, -1, false).getInstanceId();
                }
            } else if ("ICE_M_05".equals(def.cardId())) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER")
                        .stream().filter(m -> !m.isSlowedCurrentTurn()).toList();
                if (!cands.isEmpty()) {
                    targetId = cands.stream()
                            .max(Comparator.comparingInt(m -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, m)))
                            .get().getInstanceId();
                }
            }
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.PLAY_CARD,
                    card.getInstanceId(), slot, posture, targetId, null, null, null);
        }
        if (def.cardType() == CardType.FAMILIAR) {
            int slot = bot.findFirstFreeFamiliarSlot();
            Posture posture = shouldSummonInDefense(room, bot, card, difficulty) ? Posture.DEFENSE : Posture.ATTACK;
            return new GameActionCommand(
                    room.getMatchId(), null, GameActionType.PLAY_CARD,
                    card.getInstanceId(), slot, posture, null, null, null, null);
        }
        return buildPlaySpellCommand(room, bot, card, null, difficulty, false);
    }

    private GameActionCommand buildPlaySpellCommand(
            GameRoom room,
            PlayerState bot,
            CardInstance card,
            Integer modalChoice,
            AiDifficulty difficulty,
            boolean preserveSingleOppFam) {
        CardDefinition def = card.getDefinition();
        Integer slot = (def.cardType() == CardType.CURSE || def.cardType() == CardType.ARTIFACT)
                ? bot.findFirstFreeSpellSlot()
                : null;
        if ((def.cardType() == CardType.CURSE || def.cardType() == CardType.ARTIFACT) && slot < 0) {
            return null;
        }
        String targetId = resolveTargetForSpellPlay(room, bot, card, modalChoice, preserveSingleOppFam);
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.PLAY_CARD,
                card.getInstanceId(), slot, null, targetId, null, modalChoice, null);
    }

    private String resolveTargetForSpellPlay(
            GameRoom room, PlayerState bot, CardInstance card, Integer modalChoice, boolean preserveSingleOppFam) {
        String cid = card.getDefinition().cardId();
        if ("FIRE_S_01".equals(cid)) {
            CardInstance target = pickBestAllyForTurnAtkBuff(room, bot, null, 1);
            return target != null ? target.getInstanceId() : firstAllyMonsterId(bot);
        }
        if ("ICE_S_01".equals(cid)) {
            CardInstance mon = pickBestAllyMonsterToBuffDef(room, bot, null);
            return mon != null ? mon.getInstanceId() : firstAllyMonsterId(bot);
        }
        if ("LIGHT_S_01".equals(cid)) {
            return bot.getAllMonsters().stream()
                    .filter(this::isCreatureDebuffedOrSlowed)
                    .max(Comparator.comparingInt(m -> BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, m)))
                    .map(CardInstance::getInstanceId)
                    .orElse(firstAllyMonsterId(bot));
        }

        if ("FIRE_S_03".equals(cid) && Integer.valueOf(1).equals(modalChoice)) {
            return null;
        }
        if ("FIRE_S_03".equals(cid) || ("LIGHT_S_08".equals(cid) && Integer.valueOf(0).equals(modalChoice))) {
            return abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER_DEF_LE_3").stream()
                    .filter(t -> !evaluator.hasSpellDestructionImmunity(room, t))
                    .max(Comparator.comparingInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t)))
                    .map(CardInstance::getInstanceId)
                    .orElse(null);
        }
        if ("SHADOW_S_07".equals(cid)) {
            return abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER_ATK_LE_2").stream()
                    .filter(t -> !evaluator.hasSpellDestructionImmunity(room, t))
                    .max(Comparator.comparingInt(t -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, t)))
                    .map(CardInstance::getInstanceId)
                    .orElse(null);
        }

        for (AbilityDefinition ab : card.getDefinition().abilitiesScript()) {
            if ("ON_PLAY".equals(ab.trigger()) && ab.target() != null && ab.target().startsWith("OPPONENT_")
                    && !"OPPONENT_PLAYER".equals(ab.target())) {
                List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, ab.target());
                if (!cands.isEmpty()) {
                    if ("APPLY_SLOW".equals(ab.action()) || "MODIFY_ATK".equals(ab.action())) {
                        return cands.stream()
                                .filter(c -> !"APPLY_SLOW".equals(ab.action()) || !c.isSlowedCurrentTurn())
                                .max(Comparator.comparingInt(c -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, c)))
                                .orElse(cands.getFirst())
                                .getInstanceId();
                    }
                    int defDelta = "MODIFY_DEF".equals(ab.action()) || "MODIFY_ATK_AND_DEF".equals(ab.action())
                            || "MODIFY_DEF_OR_LP_IF_EMPTY".equals(ab.action())
                            ? (ab.value() != null ? ab.value() : -2) : 0;
                    int atkDelta = "MODIFY_ATK_AND_DEF".equals(ab.action())
                            ? (ab.value() != null ? ab.value() : -2) : 0;
                    CardInstance picked = pickBestEnemyDebuffTarget(room, bot, cands, atkDelta, defDelta, preserveSingleOppFam);
                    if (picked != null) {
                        return picked.getInstanceId();
                    }
                }
            }
        }
        if ("LIGHT_S_08".equals(cid) && Integer.valueOf(1).equals(modalChoice)) {
            List<CardInstance> cands = abilities.findTargetCandidates(room, bot, card, "OPPONENT_MONSTER");
            CardInstance picked = pickBestEnemyDebuffTarget(room, bot, cands, 0, -3, false);
            return picked != null ? picked.getInstanceId() : null;
        }
        return null;
    }

    private String resolveTargetForModalOption(GameRoom room, PlayerState bot, AbilityOption opt) {
        if (opt == null || opt.target() == null || !opt.target().startsWith("OPPONENT_") || "OPPONENT_PLAYER".equals(opt.target())) {
            return null;
        }
        List<CardInstance> cands = abilities.findTargetCandidates(room, bot, null, opt.target());
        if (cands.isEmpty()) {
            return null;
        }
        return cands.stream()
                .max(Comparator.comparingInt(c -> BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, c)))
                .map(CardInstance::getInstanceId)
                .orElse(cands.getFirst().getInstanceId());
    }

    private boolean shouldSummonInDefense(GameRoom room, PlayerState bot, CardInstance card, AiDifficulty difficulty) {
        CardDefinition def = card.getDefinition();
        if ("ICE_M_09".equals(def.cardId())) {
            return true; // SPELL_DESTRUCTION_IMMUNITY activates only in DEFENSE!
        }
        if (difficulty == AiDifficulty.EASY) {
            return (def.baseDef() != null ? def.baseDef() : 0) > (def.baseAtk() != null ? def.baseAtk() : 0);
        }
        int effAtk = BotComboKnowledge.estimateCreaturePlayAtk(room, bot, def, evaluator);
        int effDef = BotComboKnowledge.estimateCreaturePlayDef(room, bot, def);
        if (room.isTurn1TruceActive()) {
            return effDef >= effAtk || BotComboKnowledge.hasKeyword(def, "GARDIEN");
        }
        if (BotComboKnowledge.hasKeyword(def, "GARDIEN") && effDef > effAtk) {
            return true;
        }
        // On Turn 2+, summon in ATTACK if creature has a favorable immediate attack, otherwise defend if effDef >= effAtk
        int maxOppAtk = BotComboKnowledge.maxVisibleEnemyAttack(room, bot.getTeamId(), evaluator);
        boolean canAttackProfitably = hasSafeCombatKillTarget(room, bot, null, effAtk)
                || BotComboKnowledge.countVisibleOpponentMonsters(room, bot.getTeamId(), evaluator, true) == 0;
        if (!canAttackProfitably && effDef >= effAtk && maxOppAtk >= effAtk) {
            return true;
        }
        return effDef >= effAtk + 2 && !BotComboKnowledge.hasKeyword(def, "SURVOL");
    }

    private int evaluateEnergyUnlockDemand(
            GameRoom room, PlayerState bot, int extraEnergyProvided, AiDifficulty difficulty) {
        int cur = bot.getEnergyTokens();
        int boosted = cur + extraEnergyProvided;
        int bestUnlocked = 0;

        // Check if boosted unlocks an unplayable card right now
        for (CardInstance card : bot.getHand()) {
            CardDefinition def = card.getDefinition();
            int cost = def.energyCost();
            if (cost > cur && cost <= boosted && canPlayWithEnergyBudget(room, bot, card, boosted)) {
                bestUnlocked = Math.max(bestUnlocked, def.energyCost() * 140 + (def.isEpic() ? 220 : 80));
            }
        }
        // Check if boosted unlocks a 2-card play whose sum exceeds cur!
        if (bestUnlocked == 0 && difficulty == AiDifficulty.HARD) {
            List<CardInstance> hand = bot.getHand();
            for (int i = 0; i < hand.size(); i++) {
                for (int j = i + 1; j < hand.size(); j++) {
                    int sum = hand.get(i).getDefinition().energyCost() + hand.get(j).getDefinition().energyCost();
                    if (sum > cur && sum <= boosted) {
                        bestUnlocked = Math.max(bestUnlocked, sum * 90);
                    }
                }
            }
        }
        return bestUnlocked;
    }

    private boolean canPlayWithEnergyBudget(GameRoom room, PlayerState bot, CardInstance card, int budget) {
        CardDefinition def = card.getDefinition();
        if (def.energyCost() > budget) {
            return false;
        }
        return switch (def.cardType()) {
            case MONSTER -> bot.findFirstFreeMonsterSlot() >= 0 && (!def.isEpic() || !bot.hasEpicMonsterOnBoard());
            case FAMILIAR -> bot.findFirstFreeFamiliarSlot() >= 0;
            case CURSE, ARTIFACT -> !bot.isSpellsLockedThisTurn() && bot.findFirstFreeSpellSlot() >= 0;
            case SPELL_NORMAL, SPELL_ULTIMATE -> !bot.isSpellsLockedThisTurn()
                    && abilities.hasValidTargetForSpellPlay(room, bot, card, null);
            case SPELL_REACTIVE -> !bot.isSpellsLockedThisTurn() && bot.findFirstFreeSpellSlot() >= 0;
        };
    }

    private int evaluateHandRetentionValue(GameRoom room, PlayerState bot, CardInstance c) {
        CardDefinition def = c.getDefinition();
        int val = 100 + def.energyCost() * 20;
        if (def.isEpic() && bot.hasEpicMonsterOnBoard()) {
            val -= 140; // Unplayable duplicate epic
        }
        if (def.energyCost() >= 5 && bot.getEnergyTokens() < 4) {
            val -= 75;
        }
        if (BotComboKnowledge.isTurnStartRamp(def) || BotComboKnowledge.isEnergyRefundSpell(def)) {
            val += 110;
        }
        if (BotComboKnowledge.countPlayableDamageValue(def) > 0) {
            val += 85;
        }
        if (def.cardType() == CardType.CURSE && BotComboKnowledge.isDuplicateActiveCurse(bot, def.cardId())) {
            val -= 120;
        }
        if (def.cardType() == CardType.FAMILIAR && bot.getAllMonsters().stream()
                .anyMatch(m -> BotComboKnowledge.isAnyCondition(m.getDefinition(), "ALLY_FAMILIAR_PRESENT"))) {
            val += 70;
        }
        return val;
    }

    private int scorePlayCardBasic(GameRoom room, PlayerState bot, CardInstance c) {
        CardDefinition def = c.getDefinition();
        int atk = def.baseAtk() != null ? def.baseAtk() : 0;
        int d = def.baseDef() != null ? def.baseDef() : 0;
        return 100 + def.energyCost() * 20 + atk * 10 + d * 8;
    }

    private CardInstance pickBestEnemyDebuffTarget(
            GameRoom room,
            PlayerState bot,
            List<CardInstance> cands,
            int atkDelta,
            int defDelta,
            boolean preserveSingleOppFam) {
        return cands.stream()
                .filter(c -> {
                    if (!preserveSingleOppFam || c.getDefinition().cardType() != CardType.FAMILIAR) {
                        return true;
                    }
                    int oppFamCount = BotComboKnowledge.countVisibleOpponentFamiliars(room, bot.getTeamId(), evaluator, true);
                    boolean lethalDrop = defDelta < 0 && evaluator.evaluateEffectiveDef(room, c) + defDelta <= 0;
                    return !(oppFamCount == 1 && lethalDrop);
                })
                .max(Comparator.comparingInt(c ->
                        BotComboKnowledge.evaluateDebuffImpact(c, atkDelta, defDelta, evaluator, room)
                                + BotComboKnowledge.evaluateEnemyThreat(room, evaluator, bot, c)))
                .orElse(null);
    }

    private CardInstance pickBestAllyMonsterToBuffDef(GameRoom room, PlayerState bot, CardInstance excludeSelf) {
        return bot.getAllMonsters().stream()
                .filter(m -> excludeSelf == null || !m.getInstanceId().equals(excludeSelf.getInstanceId()))
                .max(Comparator.comparingInt(m -> BotComboKnowledge.evaluateAllyValue(room, evaluator, bot, m)
                        + (evaluator.hasKeyword(room, m, "GARDIEN") ? 40 : 0)))
                .orElse(null);
    }

    private CardInstance pickBestAllyForTurnAtkBuff(
            GameRoom room, PlayerState bot, CardInstance excludeSelf, int atkGain) {
        return bot.getAllMonsters().stream()
                .filter(m -> excludeSelf == null || !m.getInstanceId().equals(excludeSelf.getInstanceId()))
                .filter(m -> evaluator.canCreatureAttackNow(room, m)
                        || (m.getPosture() == Posture.DEFENSE && !m.isChangedPostureThisTurn() && !m.isSlowedCurrentTurn()))
                .max(Comparator.comparingInt(m -> evaluateTurnAtkBuffGain(room, bot, m, atkGain)))
                .orElse(null);
    }

    private int evaluateTurnAtkBuffGain(GameRoom room, PlayerState bot, CardInstance ally, int atkGain) {
        int curAtk = evaluator.evaluateEffectiveAtk(room, ally);
        int multi = evaluator.getMaxAttacksPerTurn(room, ally);
        int gain = atkGain * multi * 40;
        for (PlayerState opp : room.getOpponentPlayers(bot.getTeamId())) {
            for (CardInstance oppC : opp.getAllBoardCreatures()) {
                if (evaluator.hasKeyword(room, oppC, "CAMOUFLAGE")) {
                    continue;
                }
                if (oppC.getPosture() == Posture.DEFENSE) {
                    int oppDef = evaluator.evaluateEffectiveDef(room, oppC);
                    if (curAtk <= oppDef && curAtk + atkGain > oppDef) {
                        gain += 220;
                    }
                } else {
                    int oppAtk = evaluator.evaluateEffectiveAtk(room, oppC);
                    if (curAtk < oppAtk && curAtk + atkGain >= oppAtk) {
                        gain += 180;
                    } else if (curAtk == oppAtk) {
                        gain += 210; // Turns 1-for-1 trade into surviving with overflow!
                    }
                }
            }
        }
        if (evaluator.hasKeyword(room, ally, "SURVOL")) {
            gain += 60;
        }
        return gain;
    }

    private CardInstance findEnemyAtkDebuffFlippingCombatTrade(GameRoom room, PlayerState bot, int atkReduction) {
        List<CardInstance> readyAllies = bot.getAllBoardCreatures().stream()
                .filter(c -> evaluator.canCreatureAttackNow(room, c))
                .toList();
        if (readyAllies.isEmpty()) {
            return null;
        }
        for (PlayerState opp : room.getOpponentPlayers(bot.getTeamId())) {
            for (CardInstance enemy : opp.getAllMonsters()) {
                if (enemy.getPosture() != Posture.ATTACK || evaluator.hasKeyword(room, enemy, "CAMOUFLAGE")) {
                    continue;
                }
                int oppAtk = evaluator.evaluateEffectiveAtk(room, enemy);
                for (CardInstance ally : readyAllies) {
                    int myAtk = evaluator.evaluateEffectiveAtk(room, ally);
                    if (myAtk <= oppAtk && myAtk > oppAtk - atkReduction) {
                        return enemy;
                    }
                }
            }
        }
        return null;
    }

    private boolean wouldDefDropUnlockAllyKill(GameRoom room, PlayerState bot, CardInstance oppTarget, int drop) {
        int curDef = evaluator.evaluateEffectiveDef(room, oppTarget);
        for (CardInstance c : bot.getAllBoardCreatures()) {
            if (!evaluator.canCreatureAttackNow(room, c)) {
                continue;
            }
            int myAtk = evaluator.evaluateEffectiveAtk(room, c);
            if (oppTarget.getPosture() == Posture.DEFENSE && myAtk <= curDef && myAtk > curDef - drop) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSafeCombatKillTarget(GameRoom room, PlayerState bot, CardInstance optionalAttacker, int atk) {
        for (PlayerState opp : room.getOpponentPlayers(bot.getTeamId())) {
            for (CardInstance c : opp.getAllBoardCreatures()) {
                if (evaluator.hasKeyword(room, c, "CAMOUFLAGE")) {
                    continue;
                }
                if (c.getPosture() == Posture.ATTACK) {
                    if (atk > evaluator.evaluateEffectiveAtk(room, c)) {
                        return true;
                    }
                } else if (atk > evaluator.evaluateEffectiveDef(room, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSafeToStealth(GameRoom room, PlayerState bot, CardInstance familiar) {
        if (!bot.getAllMonsters().isEmpty() || bot.getAllFamiliars().size() > 1) {
            return true;
        }
        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        int maxOppAtk = BotComboKnowledge.maxVisibleEnemyAttack(room, bot.getTeamId(), evaluator);
        return teamLp > maxOppAtk;
    }

    private boolean isCreatureDebuffedOrSlowed(CardInstance c) {
        if (c.isSlowedCurrentTurn() || c.isSlowPendingNextOwnTurn()) {
            return true;
        }
        return c.getModifiers().stream().anyMatch(StatModifier::isDebuff);
    }

    private int countOtherReadyAttackers(GameRoom room, TeamId teamId, String excludeId) {
        int count = 0;
        for (PlayerState p : room.getTeamPlayers(teamId)) {
            for (CardInstance c : p.getAllBoardCreatures()) {
                if (!c.getInstanceId().equals(excludeId)
                        && c.getPosture() == Posture.ATTACK
                        && !c.isSlowedCurrentTurn()
                        && c.getAttacksMadeThisTurn() < evaluator.getMaxAttacksPerTurn(room, c)) {
                    count++;
                }
            }
        }
        return count;
    }

    private String firstAllyMonsterId(PlayerState bot) {
        return bot.getAllMonsters().stream().findFirst().map(CardInstance::getInstanceId).orElse(null);
    }

    private GameActionCommand activateCmd(
            GameRoom room, String cardId, String targetId, String secondaryTargetId, Integer choiceIndex) {
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.ACTIVATE_EFFECT,
                cardId, null, null, targetId, secondaryTargetId, choiceIndex, null);
    }

    private GameActionCommand passReaction(GameRoom room) {
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.PASS_REACTION,
                null, null, null, null, null, null, null);
    }

    private record ScoredCommand(int score, GameActionCommand command) {
    }

    private record ReactionEval(int score, Integer choiceIndex, String targetId) {
        static final ReactionEval PASS = new ReactionEval(0, null, null);
    }
}
