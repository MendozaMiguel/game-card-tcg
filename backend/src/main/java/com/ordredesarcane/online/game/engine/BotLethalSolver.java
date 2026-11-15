package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic single-turn lethal detector and execution planner for {@link MageAutomateBot}.
 * <p>
 * Evaluates strictly visible board state, ally resources, energy unlocks, blocker removals,
 * temporary buffs, posture flips, and combat ordering to spot guaranteed lethals and execute
 * steps in legal phase order ({@code MAIN_PHASE} prep before {@code COMBAT_PHASE} attacks).
 */
final class BotLethalSolver {

    private final BoardRuleEvaluator evaluator;
    private final AbilityInterpreter abilities;

    BotLethalSolver(BoardRuleEvaluator evaluator, AbilityInterpreter abilities) {
        this.evaluator = evaluator;
        this.abilities = abilities;
    }

    Optional<GameActionCommand> findLethalAction(GameRoom room, PlayerState bot) {
        if (room.isTurn1TruceActive()) {
            return Optional.empty();
        }
        TeamId oppTeamId = bot.getTeamId().opponent();
        int oppLp = room.getTeam(oppTeamId).getLp();
        if (oppLp <= 0) {
            return Optional.empty();
        }
        int oppRed = evaluator.evaluateTeamLpDamageReduction(room, oppTeamId);

        if (room.getPhase() == GamePhase.MAIN_PHASE) {
            Optional<GameActionCommand> immediateFinish = findImmediateSingleStepBurnLethal(room, bot, oppLp, oppRed);
            if (immediateFinish.isPresent()) {
                return immediateFinish;
            }
        }

        List<SimEnemy> enemies = snapshotVisibleEnemies(room, oppTeamId);
        List<SimAttacker> attackers = snapshotBoardAttackers(room, bot, oppTeamId, enemies);

        if (room.getPhase() == GamePhase.COMBAT_PHASE) {
            return planCombatPhaseLethal(room, bot, oppLp, oppRed, enemies, attackers);
        }

        // MAIN_PHASE full-turn lethal simulation (energy unlocks + removal + buffs + summons + flips + attacks)
        int unlockableEnergy = BotComboKnowledge.computeUnlockableFreeEnergy(room, bot, evaluator, true, true);
        int energyBudget = bot.getEnergyTokens() + unlockableEnergy;
        List<GameActionCommand> prepCommands = new ArrayList<>();

        // 1. Collect guaranteed burn & removal effects that increase net face lethal
        int[] energyLeft = new int[] { energyBudget };
        int simulatedBurn = 0;

        // Free board burn first (Shadows Demon, Mini-Dragon, Dragon Ombre)
        for (CardInstance ally : bot.getAllBoardCreatures()) {
            if (ally.isFaceDown() || ally.isActivatedThisTurn() || ally.isExhausted()) {
                continue;
            }
            String cid = ally.getDefinition().cardId();
            if ("SHADOW_M_08".equals(cid) && evaluator.checkCondition(room, bot, ally, "OPPONENT_HAS_FAMILIAR", null)) {
                int dmg = Math.max(0, 2 - oppRed);
                if (dmg > 0) {
                    simulatedBurn += dmg;
                    prepCommands.add(activateCmd(room, ally.getInstanceId(), null, null, null));
                }
            } else if ("FIRE_F_07".equals(cid)) {
                int dmg = Math.max(0, 1 - oppRed);
                if (dmg > 0) {
                    simulatedBurn += dmg;
                    prepCommands.add(activateCmd(room, ally.getInstanceId(), null, null, 0));
                }
            }
        }

        // Apply free board debuffs (-DEF) to knock out or soften toughest blockers
        simulatedBurn += applyFreeBoardDebuffsForLethal(room, bot, enemies, prepCommands);

        // Apply Tempête Hivernale / Flamme Expansive / Éruption / Boule de Feu / Lame Céleste / Main de l'Ombre / Rayon de Lumière if blockers exist
        simulatedBurn += applyHandRemovalAndBurnForLethal(room, bot, oppRed, enemies, attackers, energyLeft, prepCommands);

        // Apply ready ATK buffs + spell-cast boosts (e.g. Chaleur Arcanique +1 energy/+1 ATK)
        applyAtkBuffsForLethal(room, bot, enemies, attackers, energyLeft, prepCommands);

        // Summon best affordable playable attacker(s) from hand into empty slots if energy & slots allow
        addHandAttackersForLethal(room, bot, oppTeamId, enemies, attackers, energyLeft, prepCommands);

        // Account for DRAGON OMBRE ON_COMBAT_PHASE_START (2 dmg if any familiar on field)
        if (!room.isCombatPhaseStartedThisTurn() && BotComboKnowledge.anyFamiliarOnAnyBoard(room)) {
            boolean hasShadowDragon = attackers.stream()
                    .anyMatch(a -> "SHADOW_M_09".equals(a.definition.cardId()));
            if (hasShadowDragon) {
                simulatedBurn += Math.max(0, 2 - oppRed);
            }
        }

        CombatSimResult combatResult = simulateOptimalCombat(room, bot, oppLp - simulatedBurn, oppRed, enemies, attackers);
        int totalDamage = simulatedBurn + combatResult.damage();
        if (totalDamage < oppLp) {
            return Optional.empty();
        }

        // If lethal uses cards costing > current energy, trigger the next free energy unlocker first
        int neededNow = prepCommands.stream()
                .mapToInt(cmd -> resolveCommandCost(bot, cmd))
                .findFirst()
                .orElse(0);
        if (bot.getEnergyTokens() < neededNow) {
            Optional<GameActionCommand> unlock = findNextEnergyUnlockerForLethal(room, bot);
            if (unlock.isPresent()) {
                return unlock;
            }
        }

        // Return first legal prep command if one exists and is affordable right now
        for (GameActionCommand cmd : prepCommands) {
            int cost = resolveCommandCost(bot, cmd);
            if (cost <= bot.getEnergyTokens()) {
                return Optional.of(cmd);
            }
            Optional<GameActionCommand> unlock = findNextEnergyUnlockerForLethal(room, bot);
            if (unlock.isPresent()) {
                return unlock;
            }
        }

        // Flip any DEFENSE attacker needed for the simulated lethal
        for (SimAttacker atk : attackers) {
            if (atk.needsFlip && atk.participated) {
                return Optional.of(new GameActionCommand(
                        room.getMatchId(), null, GameActionType.CHANGE_POSTURE,
                        atk.instanceId, null, Posture.ATTACK, null, null, null, null));
            }
        }

        // Now all MAIN_PHASE prep is done: issue first lethal attack (or ENTER_COMBAT if SHADOW_M_09 lethal)
        if (combatResult.firstAttack() != null) {
            return Optional.of(combatResult.firstAttack());
        }
        if (!room.isCombatPhaseStartedThisTurn()) {
            boolean hasShadowDragon = bot.getAllMonsters().stream()
                    .anyMatch(m -> "SHADOW_M_09".equals(m.getDefinition().cardId()));
            if (hasShadowDragon && BotComboKnowledge.anyFamiliarOnAnyBoard(room)
                    && Math.max(0, 2 - oppRed) >= oppLp) {
                return Optional.of(new GameActionCommand(
                        room.getMatchId(), null, GameActionType.ENTER_COMBAT,
                        null, null, null, null, null, null, null));
            }
        }
        return Optional.empty();
    }

    private Optional<GameActionCommand> findImmediateSingleStepBurnLethal(
            GameRoom room, PlayerState bot, int oppLp, int oppRed) {
        // 1. Free board burn first (no energy spent)
        for (CardInstance ally : bot.getAllBoardCreatures()) {
            if (ally.isFaceDown() || ally.isActivatedThisTurn() || ally.isExhausted()) {
                continue;
            }
            String cid = ally.getDefinition().cardId();
            if ("SHADOW_M_08".equals(cid)
                    && evaluator.checkCondition(room, bot, ally, "OPPONENT_HAS_FAMILIAR", null)
                    && Math.max(0, 2 - oppRed) >= oppLp) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, null));
            }
            if ("FIRE_F_07".equals(cid) && Math.max(0, 1 - oppRed) >= oppLp) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, 0));
            }
        }

        // 2. Affordable burn spells in hand
        if (!bot.isSpellsLockedThisTurn()) {
            int oppMonCount = BotComboKnowledge.countVisibleOpponentMonsters(room, bot.getTeamId(), evaluator, true);
            for (CardInstance handCard : bot.getHand()) {
                String cid = handCard.getDefinition().cardId();
                if (handCard.getDefinition().energyCost() > bot.getEnergyTokens()) {
                    continue;
                }
                if ("FIRE_S_03".equals(cid) && Math.max(0, 2 - oppRed) >= oppLp) {
                    return Optional.of(playSpellCmd(room, handCard.getInstanceId(), null, 1));
                }
                if ("SHADOW_S_05".equals(cid) && oppMonCount == 0 && Math.max(0, 2 - oppRed) >= oppLp) {
                    return Optional.of(playSpellCmd(room, handCard.getInstanceId(), null, null));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<GameActionCommand> planCombatPhaseLethal(
            GameRoom room,
            PlayerState bot,
            int oppLp,
            int oppRed,
            List<SimEnemy> enemies,
            List<SimAttacker> attackers) {
        CombatSimResult combatResult = simulateOptimalCombat(room, bot, oppLp, oppRed, enemies, attackers);
        if (combatResult.damage() >= oppLp && combatResult.firstAttack() != null) {
            return Optional.of(combatResult.firstAttack());
        }
        return Optional.empty();
    }

    private int applyFreeBoardDebuffsForLethal(
            GameRoom room, PlayerState bot, List<SimEnemy> enemies, List<GameActionCommand> prepCommands) {
        if (enemies.isEmpty()) {
            return 0;
        }
        for (CardInstance ally : bot.getAllBoardCreatures()) {
            if (ally.isFaceDown() || ally.isActivatedThisTurn() || ally.isExhausted()) {
                continue;
            }
            String cid = ally.getDefinition().cardId();
            if ("LIGHT_M_08".equals(cid)
                    && evaluator.checkCondition(room, bot, ally, "ALLY_FAMILIAR_PRESENT", null)) {
                SimEnemy best = pickBestBlockerForDefDebuff(enemies, true, false, 2);
                if (best != null) {
                    best.def -= 2;
                    if (best.def <= 0) {
                        enemies.remove(best);
                    }
                    prepCommands.add(activateCmd(room, ally.getInstanceId(), best.instanceId, null, null));
                }
            } else if ("FIRE_M_09".equals(cid) && enemies.stream().anyMatch(e -> e.isMonster)) {
                enemies.removeIf(e -> {
                    if (e.isMonster) {
                        e.atk = Math.max(0, e.atk - 1);
                        e.def -= 1;
                        return e.def <= 0;
                    }
                    return false;
                });
                prepCommands.add(activateCmd(room, ally.getInstanceId(), null, null, null));
            } else if (("FIRE_F_10".equals(cid) || "ICE_F_10".equals(cid) || "LIGHT_F_10".equals(cid) || "ICE_M_06".equals(cid))
                    && enemies.stream().anyMatch(e -> e.isFamiliar)) {
                enemies.removeIf(e -> {
                    if (e.isFamiliar) {
                        e.def -= 1;
                        return e.def <= 0;
                    }
                    return false;
                });
                prepCommands.add(activateCmd(room, ally.getInstanceId(), null, null, null));
            } else if ("FIRE_F_06".equals(cid) || "ICE_F_06".equals(cid)
                    || "SHADOW_F_07".equals(cid) || "LIGHT_F_07".equals(cid)
                    || "SHADOW_F_06".equals(cid) || "SHADOW_F_09".equals(cid)
                    || ("SHADOW_F_05".equals(cid) && evaluator.checkCondition(room, bot, ally, "ALLY_MONSTER_PRESENT", null))
                    || ("LIGHT_F_05".equals(cid) && evaluator.checkCondition(room, bot, ally, "ALLY_MONSTER_PRESENT", null))) {
                boolean allowFam = "SHADOW_F_05".equals(cid) || "LIGHT_F_05".equals(cid);
                SimEnemy best = pickBestBlockerForDefDebuff(enemies, true, allowFam, 1);
                if (best != null) {
                    best.def -= 1;
                    if ("ICE_F_06".equals(cid) || "SHADOW_F_06".equals(cid) || "SHADOW_F_09".equals(cid)) {
                        best.atk = Math.max(0, best.atk - 1);
                    }
                    if (best.def <= 0) {
                        enemies.remove(best);
                    }
                    prepCommands.add(activateCmd(room, ally.getInstanceId(), best.instanceId, null, null));
                }
            }
        }
        return 0;
    }

    private int applyHandRemovalAndBurnForLethal(
            GameRoom room,
            PlayerState bot,
            int oppRed,
            List<SimEnemy> enemies,
            List<SimAttacker> attackers,
            int[] energyLeft,
            List<GameActionCommand> prepCommands) {
        if (bot.isSpellsLockedThisTurn()) {
            return 0;
        }
        int totalAttackerAtk = attackers.stream().mapToInt(a -> a.atk).sum();
        int burn = 0;

        // First: blocker clearing spells when blockers block high ATK face hits
        if (!enemies.isEmpty() && totalAttackerAtk > 0) {
            for (CardInstance card : bot.getHand()) {
                int cost = card.getDefinition().energyCost();
                if (cost > energyLeft[0]) {
                    continue;
                }
                String cid = card.getDefinition().cardId();
                if ("ICE_S_10".equals(cid) && enemies.stream().anyMatch(e -> e.isMonster)) {
                    enemies.removeIf(e -> {
                        if (e.isMonster) {
                            e.def -= 2;
                            e.isAttackPosture = false;
                            return e.def <= 0;
                        }
                        return false;
                    });
                    energyLeft[0] -= cost;
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, null));
                } else if ("FIRE_S_10".equals(cid) && (!enemies.isEmpty())) {
                    enemies.removeIf(e -> {
                        if (e.isFamiliar && !e.immuneToSpellDestroy) {
                            return true;
                        }
                        if (e.isMonster) {
                            e.def -= 1;
                            return e.def <= 0;
                        }
                        return false;
                    });
                    energyLeft[0] -= cost;
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, null));
                } else if (("FIRE_S_09".equals(cid) || "LIGHT_S_10".equals(cid))
                        && enemies.stream().anyMatch(e -> e.isMonster)) {
                    enemies.removeIf(e -> {
                        if (e.isMonster) {
                            e.def -= 2;
                            return e.def <= 0;
                        }
                        return false;
                    });
                    energyLeft[0] -= cost;
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, null));
                } else if ("FIRE_S_03".equals(cid)) {
                    SimEnemy destroyable = enemies.stream()
                            .filter(e -> e.isMonster && e.def <= 3 && !e.immuneToSpellDestroy)
                            .max(Comparator.comparingInt(e -> (e.isGuardian ? 100 : 0) + e.atk + e.def))
                            .orElse(null);
                    if (destroyable != null && (enemies.stream().filter(e -> e.isMonster).count() == 1 || totalAttackerAtk >= 3)) {
                        enemies.remove(destroyable);
                        energyLeft[0] -= cost;
                        prepCommands.add(playSpellCmd(room, card.getInstanceId(), destroyable.instanceId, 0));
                    }
                } else if ("LIGHT_S_08".equals(cid)) {
                    SimEnemy destroyable = enemies.stream()
                            .filter(e -> e.isMonster && e.def <= 3 && !e.immuneToSpellDestroy)
                            .max(Comparator.comparingInt(e -> (e.isGuardian ? 100 : 0) + e.atk + e.def))
                            .orElse(null);
                    if (destroyable != null) {
                        enemies.remove(destroyable);
                        energyLeft[0] -= cost;
                        prepCommands.add(playSpellCmd(room, card.getInstanceId(), destroyable.instanceId, 0));
                    } else {
                        SimEnemy tough = pickBestBlockerForDefDebuff(enemies, false, false, 3);
                        if (tough != null) {
                            tough.def -= 3;
                            if (tough.def <= 0) {
                                enemies.remove(tough);
                            }
                            energyLeft[0] -= cost;
                            prepCommands.add(playSpellCmd(room, card.getInstanceId(), tough.instanceId, 1));
                        }
                    }
                } else if ("SHADOW_S_07".equals(cid)) {
                    SimEnemy target = enemies.stream()
                            .filter(e -> e.isMonster && e.atk <= 2 && !e.immuneToSpellDestroy)
                            .max(Comparator.comparingInt(e -> (e.isGuardian ? 100 : 0) + e.def))
                            .orElse(null);
                    if (target != null) {
                        enemies.remove(target);
                        energyLeft[0] -= cost;
                        prepCommands.add(playSpellCmd(room, card.getInstanceId(), target.instanceId, null));
                    }
                } else if ("LIGHT_S_03".equals(cid) || "FIRE_S_07".equals(cid) || "SHADOW_S_08".equals(cid)) {
                    SimEnemy target = pickBestBlockerForDefDebuff(enemies, false, false, 2);
                    if (target != null) {
                        target.def -= 2;
                        if (!"LIGHT_S_03".equals(cid)) {
                            target.atk = Math.max(0, target.atk - 2);
                        }
                        if (target.def <= 0) {
                            enemies.remove(target);
                        }
                        energyLeft[0] -= cost;
                        prepCommands.add(playSpellCmd(room, card.getInstanceId(), target.instanceId, null));
                    }
                } else if (("FIRE_S_06".equals(cid) || "ICE_S_04".equals(cid) || "SHADOW_S_04".equals(cid))
                        && enemies.stream().noneMatch(e -> e.isMonster)
                        && attackers.stream().anyMatch(a -> !a.hasSurvol && a.atk >= 3)) {
                    SimEnemy fam = enemies.stream()
                            .filter(e -> e.isFamiliar && !e.immuneToSpellDestroy)
                            .findFirst()
                            .orElse(null);
                    if (fam != null) {
                        enemies.remove(fam);
                        energyLeft[0] -= cost;
                        prepCommands.add(playSpellCmd(room, card.getInstanceId(), fam.instanceId, null));
                    }
                }
            }
        }

        // Second: leftover direct LP burn cards (ignoring ones already scheduled!)
        Set<String> alreadyScheduledIds = new HashSet<>();
        for (GameActionCommand cmd : prepCommands) {
            if (cmd.cardInstanceId() != null) {
                alreadyScheduledIds.add(cmd.cardInstanceId());
            }
        }
        for (CardInstance card : bot.getHand()) {
            if (alreadyScheduledIds.contains(card.getInstanceId())) {
                continue;
            }
            int cost = card.getDefinition().energyCost();
            if (cost > energyLeft[0]) {
                continue;
            }
            String cid = card.getDefinition().cardId();
            if ("FIRE_S_03".equals(cid)) {
                int dmg = Math.max(0, 2 - oppRed);
                if (dmg > 0) {
                    burn += dmg;
                    energyLeft[0] -= cost;
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, 1));
                }
            } else if ("SHADOW_S_05".equals(cid) && enemies.stream().noneMatch(e -> e.isMonster)) {
                int dmg = Math.max(0, 2 - oppRed);
                if (dmg > 0) {
                    burn += dmg;
                    energyLeft[0] -= cost;
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, null));
                }
            }
        }
        return burn;
    }

    private void applyAtkBuffsForLethal(
            GameRoom room,
            PlayerState bot,
            List<SimEnemy> enemies,
            List<SimAttacker> attackers,
            int[] energyLeft,
            List<GameActionCommand> prepCommands) {
        if (attackers.isEmpty()) {
            return;
        }
        SimAttacker primary = attackers.stream()
                .max(Comparator.comparingInt(a -> (a.hasSurvol ? 50 : 0) + a.atk))
                .orElse(null);
        if (primary == null) {
            return;
        }

        for (CardInstance ally : bot.getAllBoardCreatures()) {
            if (ally.isFaceDown() || ally.isActivatedThisTurn() || ally.isExhausted()) {
                continue;
            }
            String cid = ally.getDefinition().cardId();
            if ("FIRE_F_03".equals(cid) || ("LIGHT_M_06".equals(cid) && !ally.getInstanceId().equals(primary.instanceId))) {
                SimAttacker buffed = "FIRE_F_03".equals(cid)
                        ? attackers.stream().filter(a -> a.isMonster).findFirst().orElse(null)
                        : attackers.stream().filter(a -> a.isMonster && !a.instanceId.equals(ally.getInstanceId())).findFirst().orElse(null);
                if (buffed != null) {
                    buffed.atk += 1;
                    prepCommands.add(activateCmd(room, ally.getInstanceId(), buffed.instanceId, null, null));
                }
            }
        }

        if (bot.isSpellsLockedThisTurn()) {
            return;
        }
        for (CardInstance card : bot.getHand()) {
            int cost = card.getDefinition().energyCost();
            if (cost > energyLeft[0]) {
                continue;
            }
            String cid = card.getDefinition().cardId();
            if ("FIRE_S_01".equals(cid)) {
                SimAttacker mon = attackers.stream().filter(a -> a.isMonster).findFirst().orElse(null);
                if (mon != null) {
                    mon.atk += 1;
                    // +1 ATK +1 energy (net 0 cost after casting)
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), mon.instanceId, null));
                    for (SimAttacker a : attackers) {
                        if (BotComboKnowledge.isOnSpellCastEngine(a.definition)) {
                            a.atk += 1;
                        }
                    }
                }
            } else if ("LIGHT_S_06".equals(cid)) {
                long readyMonsters = attackers.stream().filter(a -> a.isMonster).count();
                if (readyMonsters >= 2 && cost <= energyLeft[0]) {
                    energyLeft[0] -= cost;
                    for (SimAttacker a : attackers) {
                        if (a.isMonster) {
                            a.atk += 1;
                        }
                        if (BotComboKnowledge.isOnSpellCastEngine(a.definition)) {
                            a.atk += 1;
                        }
                    }
                    prepCommands.add(playSpellCmd(room, card.getInstanceId(), null, null));
                }
            }
        }
    }

    private void addHandAttackersForLethal(
            GameRoom room,
            PlayerState bot,
            TeamId oppTeamId,
            List<SimEnemy> enemies,
            List<SimAttacker> attackers,
            int[] energyLeft,
            List<GameActionCommand> prepCommands) {
        int freeMonSlots = countFreeSlots(bot.getMonsters());
        int freeFamSlots = countFreeSlots(bot.getFamiliars());
        boolean hasEpic = bot.hasEpicMonsterOnBoard();

        Set<String> scheduled = new HashSet<>();
        for (GameActionCommand cmd : prepCommands) {
            if (cmd.cardInstanceId() != null) {
                scheduled.add(cmd.cardInstanceId());
            }
        }

        List<CardInstance> summonable = bot.getHand().stream()
                .filter(c -> !scheduled.contains(c.getInstanceId()))
                .filter(c -> !c.getDefinition().cardType().isSpellCategory())
                .filter(c -> c.getDefinition().energyCost() <= energyLeft[0])
                .sorted(Comparator
                        .<CardInstance>comparingInt(c -> BotComboKnowledge.estimateCreaturePlayAtk(room, bot, c.getDefinition(), evaluator)
                                + (BotComboKnowledge.hasKeyword(c.getDefinition(), "SURVOL") ? 3 : 0))
                        .reversed())
                .toList();

        boolean oppHasFam = !enemies.isEmpty() && enemies.stream().anyMatch(e -> e.isFamiliar);
        for (CardInstance c : summonable) {
            CardDefinition def = c.getDefinition();
            if (def.energyCost() > energyLeft[0]) {
                continue;
            }
            if (def.cardType() == CardType.MONSTER) {
                if (freeMonSlots <= 0 || (def.isEpic() && hasEpic)) {
                    continue;
                }
                int slot = firstFreeSlotAfter(bot.getMonsters(), PlayerState.MAX_MONSTER_SLOTS - freeMonSlots);
                if (slot < 0) {
                    continue;
                }
                freeMonSlots--;
                if (def.isEpic()) {
                    hasEpic = true;
                }
                energyLeft[0] -= def.energyCost();
                int atk = BotComboKnowledge.estimateCreaturePlayAtk(room, bot, def, evaluator);
                int maxAtks = ("FIRE_M_02".equals(def.cardId()) && oppHasFam) ? 2 : 1;
                attackers.add(new SimAttacker(
                        c.getInstanceId(), def, true,
                        BotComboKnowledge.hasKeyword(def, "SURVOL"),
                        false, atk, maxAtks, oppHasFam));
                prepCommands.add(new GameActionCommand(
                        room.getMatchId(), null, GameActionType.PLAY_CARD,
                        c.getInstanceId(), slot, Posture.ATTACK, null, null, null, null));
            } else if (def.cardType() == CardType.FAMILIAR && freeFamSlots > 0) {
                int atk = BotComboKnowledge.estimateCreaturePlayAtk(room, bot, def, evaluator);
                if (atk <= 0) {
                    continue;
                }
                int slot = firstFreeSlotAfter(bot.getFamiliars(), PlayerState.MAX_FAMILIAR_SLOTS - freeFamSlots);
                if (slot < 0) {
                    continue;
                }
                freeFamSlots--;
                energyLeft[0] -= def.energyCost();
                attackers.add(new SimAttacker(
                        c.getInstanceId(), def, false,
                        BotComboKnowledge.hasKeyword(def, "SURVOL"),
                        false, atk, 1, oppHasFam));
                prepCommands.add(new GameActionCommand(
                        room.getMatchId(), null, GameActionType.PLAY_CARD,
                        c.getInstanceId(), slot, Posture.ATTACK, null, null, null, null));
            }
        }
    }

    private CombatSimResult simulateOptimalCombat(
            GameRoom room,
            PlayerState bot,
            int neededDamage,
            int oppRed,
            List<SimEnemy> enemies,
            List<SimAttacker> attackers) {
        if (attackers.isEmpty()) {
            return new CombatSimResult(0, null);
        }

        // Work on cloned copies of attackers & enemies
        List<SimEnemy> remEnemies = new ArrayList<>(enemies.size());
        for (SimEnemy e : enemies) {
            remEnemies.add(e.copy());
        }
        List<SimAttacker> remAtks = new ArrayList<>(attackers.size());
        for (SimAttacker a : attackers) {
            a.participated = false;
            remAtks.add(a.copy());
        }

        int totalDmg = 0;
        GameActionCommand firstCmd = null;
        int oppFaceDownCount = BotComboKnowledge.countVisibleOpponentFaceDownSpells(room, bot.getTeamId());

        // Step A: clear non-camouflaged GARDIENs first, then non-camouflaged MONSTERs, then non-camouflaged FAMILIARs if needed for non-SURVOL
        while (true) {
            boolean anyGuard = remEnemies.stream().anyMatch(e -> e.isGuardian);
            boolean anyMon = remEnemies.stream().anyMatch(e -> e.isMonster);
            boolean anyFam = remEnemies.stream().anyMatch(e -> e.isFamiliar);

            // Find legal face-attackers right now
            List<SimAttacker> directReady = remAtks.stream()
                    .filter(a -> a.remainingAttacks > 0)
                    .filter(a -> !anyGuard && !anyMon && (!anyFam || a.hasSurvol))
                    .toList();

            // Check if no monsters/guardians remain AND (either no familiars or all remaining value comes from SURVOL)
            if (!anyGuard && !anyMon) {
                int survolDmg = 0;
                for (SimAttacker a : remAtks) {
                    if (a.remainingAttacks > 0 && (a.hasSurvol || !anyFam)) {
                        survolDmg += a.remainingAttacks * singleDirectStrikeDamage(room, bot, a, oppRed);
                    }
                }
                if (survolDmg >= neededDamage - totalDmg) {
                    // Lethal direct! Pick smallest overshooter if opp has face-down trap and excess allows baiting
                    SimAttacker chosen = pickDirectAttackerOrder(remAtks, anyFam, totalDmg, survolDmg, neededDamage, oppRed, room, bot, oppFaceDownCount);
                    if (chosen != null) {
                        chosen.participated = true;
                        markOriginalParticipated(attackers, chosen.instanceId);
                        firstCmd = new GameActionCommand(
                                room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                                chosen.instanceId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null, null, null);
                        return new CombatSimResult(totalDmg + survolDmg, firstCmd);
                    }
                }
            }

            // Need to attack a blocker: Guardians -> Monsters -> Familiars
            List<SimEnemy> mustClearPool = remEnemies.stream()
                    .filter(e -> !anyGuard || e.isGuardian)
                    .filter(e -> anyGuard || (!anyMon || e.isMonster))
                    .toList();

            if (mustClearPool.isEmpty()) {
                break;
            }

            SimEnemy bestTarget = null;
            SimAttacker bestClearer = null;
            int bestClearScore = Integer.MIN_VALUE;

            for (SimEnemy e : mustClearPool) {
                // Skip attacking familiar when we only have SURVOL left (won't block SURVOL)
                if (!anyGuard && !anyMon && e.isFamiliar
                        && remAtks.stream().noneMatch(a -> a.remainingAttacks > 0 && !a.hasSurvol)) {
                    continue;
                }
                for (SimAttacker a : remAtks) {
                    if (a.remainingAttacks <= 0) {
                        continue;
                    }
                    int effectiveAtk = a.atk + (("FIRE_M_10".equals(a.definition.cardId())
                            && remEnemies.stream().anyMatch(x -> x.isFamiliar)) ? 2 : 0);
                    boolean kills;
                    boolean survives;
                    int lpDelta = 0;
                    if (e.isAttackPosture) {
                        kills = effectiveAtk >= e.atk;
                        survives = effectiveAtk > e.atk;
                        if (effectiveAtk > e.atk) {
                            lpDelta += Math.max(0, (effectiveAtk - e.atk) - oppRed);
                        }
                    } else {
                        kills = effectiveAtk > e.def;
                        survives = true;
                    }
                    if (!kills) {
                        continue;
                    }
                    // Add trigger bonuses
                    if (e.isFamiliar && "SHADOW_M_05".equals(a.definition.cardId())) {
                        lpDelta += Math.max(0, 2 - oppRed);
                    }
                    if ("ICE_M_02".equals(a.definition.cardId()) && remEnemies.stream().anyMatch(x -> x.isFamiliar)) {
                        lpDelta += Math.max(0, 1 - oppRed);
                    }
                    if ("LIGHT_M_10".equals(a.definition.cardId()) && !bot.getAllFamiliars().isEmpty()) {
                        lpDelta += Math.max(0, 1 - oppRed);
                    }
                    if (survives && "FIRE_M_01".equals(a.definition.cardId())) {
                        lpDelta += Math.max(0, 1 - oppRed);
                    }
                    // Prefer surviving, keeping SURVOL / highest ATK free for direct face hits!
                    int score = (survives ? 500 : 120)
                            + lpDelta * 40
                            + (e.isGuardian ? 80 : 0)
                            + (a.hasSurvol ? -150 : 0)
                            - a.atk * 15;
                    if ("ICE_F_08".equals(e.cardId)) {
                        score -= 220; // Hérisson slows attacker
                    }
                    if (score > bestClearScore) {
                        bestClearScore = score;
                        bestTarget = e;
                        bestClearer = a;
                    }
                }
            }

            if (bestTarget == null || bestClearer == null) {
                break;
            }

            // Simulate step resolution
            int effAtk = bestClearer.atk + (("FIRE_M_10".equals(bestClearer.definition.cardId())
                    && remEnemies.stream().anyMatch(x -> x.isFamiliar)) ? 2 : 0);
            if (bestTarget.isAttackPosture && effAtk > bestTarget.atk) {
                totalDmg += Math.max(0, (effAtk - bestTarget.atk) - oppRed);
            }
            if (bestTarget.isFamiliar && "SHADOW_M_05".equals(bestClearer.definition.cardId())) {
                totalDmg += Math.max(0, 2 - oppRed);
            }
            if ("ICE_M_02".equals(bestClearer.definition.cardId()) && remEnemies.stream().anyMatch(x -> x.isFamiliar)) {
                totalDmg += Math.max(0, 1 - oppRed);
            }
            if ("LIGHT_M_10".equals(bestClearer.definition.cardId()) && !bot.getAllFamiliars().isEmpty()) {
                totalDmg += Math.max(0, 1 - oppRed);
            }
            if (bestTarget.isMonster && "SHADOW_M_07".equals(bestClearer.definition.cardId())) {
                remEnemies.stream().filter(x -> x.isFamiliar && x.def == 1).findFirst().ifPresent(remEnemies::remove);
            }

            boolean survivesCombat = !bestTarget.isAttackPosture || effAtk > bestTarget.atk;
            if (survivesCombat && "FIRE_M_01".equals(bestClearer.definition.cardId())) {
                totalDmg += Math.max(0, 1 - oppRed);
            }
            bestClearer.remainingAttacks--;
            if (!survivesCombat || "ICE_F_08".equals(bestTarget.cardId)) {
                bestClearer.remainingAttacks = 0;
            }
            bestClearer.participated = true;
            markOriginalParticipated(attackers, bestClearer.instanceId);
            remEnemies.remove(bestTarget);

            if (firstCmd == null) {
                Integer modalOpt = "FIRE_M_10".equals(bestClearer.definition.cardId()) ? 0 : null;
                firstCmd = new GameActionCommand(
                        room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                        bestClearer.instanceId, null, null, bestTarget.instanceId, null, modalOpt, null);
            }
            if (totalDmg >= neededDamage) {
                return new CombatSimResult(totalDmg, firstCmd);
            }
        }

        // Remaining direct strikes (if board was cleared during loop)
        boolean anyGuard = remEnemies.stream().anyMatch(e -> e.isGuardian);
        boolean anyMon = remEnemies.stream().anyMatch(e -> e.isMonster);
        boolean anyFam = remEnemies.stream().anyMatch(e -> e.isFamiliar);
        if (!anyGuard && !anyMon) {
            int faceSum = 0;
            for (SimAttacker a : remAtks) {
                if (a.remainingAttacks > 0 && (a.hasSurvol || !anyFam)) {
                    a.participated = true;
                    markOriginalParticipated(attackers, a.instanceId);
                    faceSum += a.remainingAttacks * singleDirectStrikeDamage(room, bot, a, oppRed);
                    if (firstCmd == null) {
                        firstCmd = new GameActionCommand(
                                room.getMatchId(), null, GameActionType.DECLARE_ATTACK,
                                a.instanceId, null, null, CombatResolver.DIRECT_PLAYER_TARGET, null, null, null);
                    }
                }
            }
            totalDmg += faceSum;
        }
        return new CombatSimResult(totalDmg, firstCmd);
    }

    private SimAttacker pickDirectAttackerOrder(
            List<SimAttacker> remAtks,
            boolean anyFam,
            int dmgSoFar,
            int totalFaceDmg,
            int neededDamage,
            int oppRed,
            GameRoom room,
            PlayerState bot,
            int oppFaceDownCount) {
        List<SimAttacker> eligible = remAtks.stream()
                .filter(a -> a.remainingAttacks > 0 && (a.hasSurvol || !anyFam))
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }
        // If opp has face-down trap (potential CANCEL_ATTACK) and remaining surplus covers an extra strike, lead with smaller attacker first
        if (oppFaceDownCount > 0 && eligible.size() >= 2) {
            SimAttacker smallest = eligible.stream()
                    .min(Comparator.comparingInt(a -> singleDirectStrikeDamage(room, bot, a, oppRed)))
                    .orElse(eligible.getFirst());
            int minDmg = singleDirectStrikeDamage(room, bot, smallest, oppRed);
            if (dmgSoFar + (totalFaceDmg - minDmg) >= neededDamage) {
                return smallest;
            }
        }
        return eligible.stream()
                .max(Comparator.comparingInt(a -> singleDirectStrikeDamage(room, bot, a, oppRed)))
                .orElse(eligible.getFirst());
    }

    private int singleDirectStrikeDamage(GameRoom room, PlayerState bot, SimAttacker a, int oppRed) {
        int raw = a.atk;
        String cid = a.definition.cardId();
        int bonus = 0;
        if ("FIRE_M_10".equals(cid) && a.oppHasFamiliarSnapshot) {
            raw += 2;
        }
        if ("ICE_M_02".equals(cid) && a.oppHasFamiliarSnapshot) {
            bonus += Math.max(0, 1 - oppRed);
        }
        if ("LIGHT_M_10".equals(cid) && !bot.getAllFamiliars().isEmpty()) {
            bonus += Math.max(0, 1 - oppRed);
        }
        if ("FIRE_M_01".equals(cid)) {
            bonus += Math.max(0, 1 - oppRed);
        }
        return Math.max(0, raw - oppRed) + bonus;
    }

    private Optional<GameActionCommand> findNextEnergyUnlockerForLethal(GameRoom room, PlayerState bot) {
        // Prefer free energy gains without sacrificing first
        for (CardInstance ally : bot.getAllBoardCreatures()) {
            if (ally.isFaceDown() || ally.isActivatedThisTurn() || ally.isExhausted()) {
                continue;
            }
            String cid = ally.getDefinition().cardId();
            if ("SHADOW_F_02".equals(cid) && !ally.isFirstCamouflageUsed()) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, null));
            }
            if ("ICE_F_02".equals(cid) && room.getTeam(bot.getTeamId()).getLp() >= 20) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, null));
            }
            if ("LIGHT_F_04".equals(cid) && bot.isHealedThisTurn()) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, null));
            }
            if ("SHADOW_F_03".equals(cid) && room.getTeam(bot.getTeamId()).getLp() > 1) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, 0));
            }
        }
        // Heal -> Astral Fairy combo
        if (BotComboKnowledge.canUnblockHealForAstralFairy(room, bot)) {
            for (CardInstance ally : bot.getAllBoardCreatures()) {
                if (BotComboKnowledge.hasReadyActivatedHeal(room, bot, ally)) {
                    Integer opt = "SHADOW_F_03".equals(ally.getDefinition().cardId()) ? 1 : null;
                    return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, opt));
                }
            }
        }
        for (CardInstance ally : bot.getAllFamiliars()) {
            if (!ally.isFaceDown() && !ally.isActivatedThisTurn() && !ally.isExhausted()
                    && "FIRE_F_05".equals(ally.getDefinition().cardId())) {
                return Optional.of(activateCmd(room, ally.getInstanceId(), null, null, null));
            }
        }
        return Optional.empty();
    }

    private List<SimEnemy> snapshotVisibleEnemies(GameRoom room, TeamId oppTeamId) {
        List<SimEnemy> result = new ArrayList<>();
        for (PlayerState opp : room.getTeamPlayers(oppTeamId)) {
            for (CardInstance c : opp.getAllBoardCreatures()) {
                if (evaluator.hasKeyword(room, c, "CAMOUFLAGE")) {
                    continue;
                }
                boolean isMon = c.getDefinition().cardType() == CardType.MONSTER;
                result.add(new SimEnemy(
                        c.getInstanceId(),
                        c.getDefinition().cardId(),
                        isMon,
                        !isMon,
                        evaluator.hasKeyword(room, c, "GARDIEN"),
                        c.getPosture() == Posture.ATTACK,
                        evaluator.evaluateEffectiveAtk(room, c),
                        evaluator.evaluateEffectiveDef(room, c),
                        evaluator.hasSpellDestructionImmunity(room, c)));
            }
        }
        return result;
    }

    private List<SimAttacker> snapshotBoardAttackers(
            GameRoom room, PlayerState bot, TeamId oppTeamId, List<SimEnemy> enemies) {
        List<SimAttacker> list = new ArrayList<>();
        boolean oppHasFam = enemies.stream().anyMatch(e -> e.isFamiliar)
                || BotComboKnowledge.countVisibleOpponentFamiliars(room, bot.getTeamId(), evaluator, false) > 0;

        for (CardInstance c : bot.getAllBoardCreatures()) {
            if (c.isSlowedCurrentTurn()) {
                continue;
            }
            int maxAtks = evaluator.getMaxAttacksPerTurn(room, c);
            int remaining = maxAtks - c.getAttacksMadeThisTurn();
            if (remaining <= 0) {
                continue;
            }
            boolean needsFlip = c.getPosture() == Posture.DEFENSE;
            if (needsFlip && (room.getPhase() != GamePhase.MAIN_PHASE || c.isChangedPostureThisTurn())) {
                continue;
            }
            int atk = evaluator.evaluateEffectiveAtk(room, c);
            list.add(new SimAttacker(
                    c.getInstanceId(),
                    c.getDefinition(),
                    c.getDefinition().cardType() == CardType.MONSTER,
                    evaluator.hasKeyword(room, c, "SURVOL"),
                    needsFlip,
                    atk,
                    remaining,
                    oppHasFam));
        }
        return list;
    }

    private SimEnemy pickBestBlockerForDefDebuff(
            List<SimEnemy> enemies, boolean prioritizeZeroKill, boolean allowFamiliar, int defDelta) {
        return enemies.stream()
                .filter(e -> e.isMonster || allowFamiliar)
                .max(Comparator.comparingInt(e -> {
                    int bonus = (prioritizeZeroKill && e.def <= defDelta) ? 120 : 0;
                    return bonus + (e.isGuardian ? 80 : 0) + e.atk * 10 + e.def * 6;
                }))
                .orElse(null);
    }

    private int resolveCommandCost(PlayerState bot, GameActionCommand cmd) {
        if (cmd.actionType() == GameActionType.PLAY_CARD && cmd.cardInstanceId() != null) {
            return bot.findInHand(cmd.cardInstanceId()).map(c -> c.getDefinition().energyCost()).orElse(0);
        }
        return 0;
    }

    private int countFreeSlots(CardInstance[] slots) {
        int free = 0;
        for (CardInstance c : slots) {
            if (c == null) {
                free++;
            }
        }
        return free;
    }

    private int firstFreeSlotAfter(CardInstance[] slots, int nthIndex) {
        int seen = 0;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                if (seen == nthIndex) {
                    return i;
                }
                seen++;
            }
        }
        return -1;
    }

    private void markOriginalParticipated(List<SimAttacker> originals, String instanceId) {
        for (SimAttacker a : originals) {
            if (a.instanceId.equals(instanceId)) {
                a.participated = true;
                return;
            }
        }
    }

    private GameActionCommand activateCmd(
            GameRoom room, String cardId, String targetId, String secondaryTargetId, Integer choiceIndex) {
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.ACTIVATE_EFFECT,
                cardId, null, null, targetId, secondaryTargetId, choiceIndex, null);
    }

    private GameActionCommand playSpellCmd(GameRoom room, String cardId, String targetId, Integer choiceIndex) {
        return new GameActionCommand(
                room.getMatchId(), null, GameActionType.PLAY_CARD,
                cardId, null, null, targetId, null, choiceIndex, null);
    }

    private static final class SimEnemy {
        final String instanceId;
        final String cardId;
        final boolean isMonster;
        final boolean isFamiliar;
        final boolean isGuardian;
        boolean isAttackPosture;
        int atk;
        int def;
        final boolean immuneToSpellDestroy;

        SimEnemy(String instanceId, String cardId, boolean isMonster, boolean isFamiliar,
                 boolean isGuardian, boolean isAttackPosture, int atk, int def, boolean immuneToSpellDestroy) {
            this.instanceId = instanceId;
            this.cardId = cardId;
            this.isMonster = isMonster;
            this.isFamiliar = isFamiliar;
            this.isGuardian = isGuardian;
            this.isAttackPosture = isAttackPosture;
            this.atk = atk;
            this.def = def;
            this.immuneToSpellDestroy = immuneToSpellDestroy;
        }

        SimEnemy copy() {
            return new SimEnemy(instanceId, cardId, isMonster, isFamiliar, isGuardian, isAttackPosture, atk, def, immuneToSpellDestroy);
        }
    }

    private static final class SimAttacker {
        final String instanceId;
        final CardDefinition definition;
        final boolean isMonster;
        final boolean hasSurvol;
        final boolean needsFlip;
        int atk;
        int remainingAttacks;
        final boolean oppHasFamiliarSnapshot;
        boolean participated;

        SimAttacker(String instanceId, CardDefinition definition, boolean isMonster,
                    boolean hasSurvol, boolean needsFlip, int atk, int remainingAttacks, boolean oppHasFamiliarSnapshot) {
            this.instanceId = instanceId;
            this.definition = definition;
            this.isMonster = isMonster;
            this.hasSurvol = hasSurvol;
            this.needsFlip = needsFlip;
            this.atk = atk;
            this.remainingAttacks = remainingAttacks;
            this.oppHasFamiliarSnapshot = oppHasFamiliarSnapshot;
        }

        SimAttacker copy() {
            return new SimAttacker(instanceId, definition, isMonster, hasSurvol, needsFlip, atk, remainingAttacks, oppHasFamiliarSnapshot);
        }
    }

    private record CombatSimResult(int damage, GameActionCommand firstAttack) {
    }
}
