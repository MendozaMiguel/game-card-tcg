package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain heuristics and catalog-derived combo knowledge for {@link MageAutomateBot}.
 * <p>
 * All inspections operate strictly on Fog-of-War safe data (bot's own private hand/state and public
 * board/discard/LP zones). Opponent hands, decks, and face-down spells are never inspected.
 */
final class BotComboKnowledge {

    private BotComboKnowledge() {
    }

    static boolean hasAbility(CardDefinition def, String trigger, String action) {
        return def.abilitiesScript().stream()
                .anyMatch(a -> trigger.equals(a.trigger()) && action.equals(a.action()));
    }

    static boolean hasTrigger(CardDefinition def, String trigger) {
        return def.abilitiesScript().stream().anyMatch(a -> trigger.equals(a.trigger()));
    }

    static boolean hasCondition(CardDefinition def, String trigger, String condition) {
        return def.abilitiesScript().stream()
                .anyMatch(a -> (trigger == null || trigger.equals(a.trigger())) && condition.equals(a.condition()));
    }

    static boolean isAnyCondition(CardDefinition def, String condition) {
        return hasCondition(def, null, condition);
    }

    static boolean hasKeyword(CardDefinition def, String keyword) {
        return def.keywords().contains(keyword);
    }

    static boolean isEnergyRefundSpell(CardDefinition def) {
        return def.cardType().isSpellCategory() && hasAbility(def, "ON_PLAY", "GAIN_ENERGY");
    }

    static boolean isTurnStartRamp(CardDefinition def) {
        return hasAbility(def, "ON_TURN_START", "GAIN_ENERGY");
    }

    static boolean isOnSpellCastEngine(CardDefinition def) {
        return hasTrigger(def, "ON_SPELL_CAST");
    }

    static boolean isSingleTurnCombatOnlyBuff(CardDefinition def) {
        if (def.cardType() != CardType.SPELL_NORMAL && def.cardType() != CardType.FAMILIAR && def.cardType() != CardType.MONSTER) {
            return false;
        }
        boolean hasAtkBuff = def.abilitiesScript().stream().anyMatch(a ->
                ("ON_PLAY".equals(a.trigger()) || "ACTIVATED".equals(a.trigger()))
                        && ("MODIFY_ATK".equals(a.action()) || "MODIFY_ATK_AND_DEF".equals(a.action()))
                        && a.value() != null && a.value() > 0
                        && ("END_OF_TURN".equals(a.duration()) || a.duration() == null || a.duration().isBlank()));
        if (!hasAtkBuff) {
            return false;
        }
        return def.abilitiesScript().stream()
                .noneMatch(a -> "END_OF_NEXT_OPPONENT_TURN".equals(a.duration())
                        || "PERMANENT".equals(a.duration())
                        || "GAIN_ENERGY".equals(a.action())
                        || "HEAL_LP".equals(a.action())
                        || "DRAW_CARD".equals(a.action()));
    }

    static int countPlayableDamageValue(CardDefinition def) {
        int dmg = 0;
        for (AbilityDefinition ab : def.abilitiesScript()) {
            if ("ON_PLAY".equals(ab.trigger()) || "ACTIVATED".equals(ab.trigger())) {
                if ("DAMAGE_LP".equals(ab.action()) && ab.value() != null) {
                    dmg += ab.value();
                } else if ("MODAL_CHOICE".equals(ab.action()) && ab.options() != null) {
                    dmg += ab.options().stream()
                            .filter(opt -> "DAMAGE_LP".equals(opt.action()) && opt.value() != null)
                            .mapToInt(opt -> opt.value())
                            .max()
                            .orElse(0);
                }
            }
        }
        return dmg;
    }

    static boolean hasReadyActivatedHeal(GameRoom room, PlayerState bot, CardInstance card) {
        if (card.isFaceDown() || card.isActivatedThisTurn() || card.isExhausted()) {
            return false;
        }
        String cid = card.getDefinition().cardId();
        if ("ICE_F_02".equals(cid)) {
            return room.getTeam(bot.getTeamId()).getLp() < 20;
        }
        if ("SHADOW_F_03".equals(cid)) {
            return room.getTeam(bot.getTeamId()).getLp() < room.getTeam(bot.getTeamId()).getMaxLp();
        }
        return hasAbility(card.getDefinition(), "ACTIVATED", "HEAL_LP");
    }

    /**
     * Computes additional energy immediately unlockable on bot's current turn via free/zero-cost
     * board activations without cheating or mutating room state.
     */
    static int computeUnlockableFreeEnergy(
            GameRoom room,
            PlayerState bot,
            BoardRuleEvaluator evaluator,
            boolean allowSacrifice,
            boolean allowBloodPact) {
        int extra = 0;
        boolean healAvailableThisTurn = bot.isHealedThisTurn()
                || bot.getAllBoardCreatures().stream().anyMatch(c -> hasReadyActivatedHeal(room, bot, c))
                || bot.getHand().stream().anyMatch(c ->
                        c.getDefinition().energyCost() <= bot.getEnergyTokens()
                                && !bot.isSpellsLockedThisTurn()
                                && hasAbility(c.getDefinition(), "ON_PLAY", "HEAL_LP"));

        int teamLp = room.getTeam(bot.getTeamId()).getLp();
        for (CardInstance c : bot.getAllBoardCreatures()) {
            if (c.isFaceDown() || c.isActivatedThisTurn() || c.isExhausted()) {
                continue;
            }
            String cid = c.getDefinition().cardId();
            if ("FIRE_F_05".equals(cid) && allowSacrifice) {
                extra += 2;
            } else if ("SHADOW_F_02".equals(cid) && !c.isFirstCamouflageUsed()) {
                extra += 1;
            } else if ("ICE_F_02".equals(cid) && teamLp >= 20) {
                extra += 1;
            } else if ("LIGHT_F_04".equals(cid) && healAvailableThisTurn) {
                extra += 1;
            } else if ("SHADOW_F_03".equals(cid) && allowBloodPact && teamLp > 6) {
                extra += 1;
            }
        }
        return extra;
    }

    static boolean canUnblockHealForAstralFairy(GameRoom room, PlayerState bot) {
        if (bot.isHealedThisTurn()) {
            return false;
        }
        boolean hasUnactivatedAstralFairy = bot.getAllFamiliars().stream()
                .anyMatch(f -> "LIGHT_F_04".equals(f.getDefinition().cardId())
                        && !f.isActivatedThisTurn()
                        && !f.isExhausted());
        if (!hasUnactivatedAstralFairy) {
            return false;
        }
        return true;
    }

    /**
     * Checks whether the opponent currently has only one visible non-camouflaged familiar on board
     * and the bot has an unspent payoff that relies on {@code OPPONENT_HAS_FAMILIAR}.
     */
    static boolean shouldPreserveSingleOpponentFamiliar(GameRoom room, PlayerState bot, BoardRuleEvaluator evaluator) {
        List<CardInstance> oppFamiliars = room.getOpponentPlayers(bot.getTeamId()).stream()
                .flatMap(p -> p.getAllFamiliars().stream())
                .filter(f -> !evaluator.hasKeyword(room, f, "CAMOUFLAGE"))
                .toList();
        if (oppFamiliars.size() != 1) {
            return false;
        }

        boolean hasReadyAbyssDemon = bot.getAllMonsters().stream()
                .anyMatch(m -> "SHADOW_M_08".equals(m.getDefinition().cardId())
                        && !m.isActivatedThisTurn()
                        && !m.isExhausted());
        if (hasReadyAbyssDemon) {
            return true;
        }
        if (room.isTurn1TruceActive()) {
            return false;
        }
        return bot.getAllMonsters().stream().anyMatch(m -> {
            String cid = m.getDefinition().cardId();
            boolean ready = evaluator.canCreatureAttackNow(room, m)
                    || (!m.isChangedPostureThisTurn() && !m.isSlowedCurrentTurn());
            return ready && ("FIRE_M_02".equals(cid)
                    || "FIRE_M_10".equals(cid)
                    || "ICE_M_02".equals(cid)
                    || "SHADOW_M_05".equals(cid));
        });
    }

    static int countVisibleOpponentFamiliars(GameRoom room, TeamId botTeam, BoardRuleEvaluator evaluator, boolean onlyNonCamouflaged) {
        return (int) room.getOpponentPlayers(botTeam).stream()
                .flatMap(p -> p.getAllFamiliars().stream())
                .filter(f -> !onlyNonCamouflaged || !evaluator.hasKeyword(room, f, "CAMOUFLAGE"))
                .count();
    }

    static int countVisibleOpponentMonsters(GameRoom room, TeamId botTeam, BoardRuleEvaluator evaluator, boolean onlyNonCamouflaged) {
        return (int) room.getOpponentPlayers(botTeam).stream()
                .flatMap(p -> p.getAllMonsters().stream())
                .filter(m -> !onlyNonCamouflaged || !evaluator.hasKeyword(room, m, "CAMOUFLAGE"))
                .count();
    }

    static boolean anyFamiliarOnAnyBoard(GameRoom room) {
        return room.getPlayers().values().stream().anyMatch(p -> !p.getAllFamiliars().isEmpty());
    }

    /**
     * Unified threat assessment for an opponent creature (higher = higher priority to remove/slow/clear).
     */
    static int evaluateEnemyThreat(GameRoom room, BoardRuleEvaluator evaluator, PlayerState bot, CardInstance enemy) {
        CardDefinition def = enemy.getDefinition();
        int atk = evaluator.evaluateEffectiveAtk(room, enemy);
        int effDef = evaluator.evaluateEffectiveDef(room, enemy);
        int score = atk * 16 + effDef * 10 + def.energyCost() * 8;

        String cid = def.cardId();
        if ("SHADOW_M_10".equals(cid)) {
            score += 55; // Infinite permanent ATK scaling every turn end
        } else if ("LIGHT_M_09".equals(cid)) {
            score += 50; // Global +1 DEF aura & LP damage reduction
        } else if ("ICE_M_10".equals(cid) || "FIRE_M_09".equals(cid)) {
            score += 36;
        } else if ("FIRE_M_07".equals(cid) || "ICE_M_07".equals(cid) || "SHADOW_M_09".equals(cid)) {
            score += 32;
        } else if ("FIRE_M_02".equals(cid) && !bot.getAllFamiliars().isEmpty()) {
            score += 38; // Double attack active against our familiars
        } else if ("ICE_F_09".equals(cid) || "FIRE_F_09".equals(cid)) {
            score += 36; // Energy ramp familiar
        } else if (isOnSpellCastEngine(def) || hasAbility(def, "ON_TURN_END", "HEAL_LP")
                || hasAbility(def, "ACTIVATED", "HEAL_LP")) {
            score += 24;
        }

        if (evaluator.hasKeyword(room, enemy, "GARDIEN")) {
            score += 30;
        }
        if (evaluator.hasKeyword(room, enemy, "SURVOL")) {
            score += 24;
        }
        if ("SHADOW_M_08".equals(cid) && !bot.getAllFamiliars().isEmpty()) {
            score += 30;
        }
        if (enemy.isSlowedCurrentTurn()) {
            score -= 14;
        }
        return Math.max(5, score);
    }

    /**
     * Unified retention/protection value for an allied creature (higher = avoid sacrificing or trading down).
     */
    static int evaluateAllyValue(GameRoom room, BoardRuleEvaluator evaluator, PlayerState bot, CardInstance ally) {
        CardDefinition def = ally.getDefinition();
        int atk = evaluator.evaluateEffectiveAtk(room, ally);
        int effDef = evaluator.evaluateEffectiveDef(room, ally);
        int value = atk * 15 + effDef * 11 + def.energyCost() * 8;

        String cid = def.cardId();
        if ("SHADOW_M_10".equals(cid)) {
            value += 60;
        } else if ("LIGHT_M_09".equals(cid)) {
            value += 55;
        } else if ("ICE_F_09".equals(cid)) {
            value += 40;
        } else if ("FIRE_F_09".equals(cid)) {
            value += bot.hasEpicMonsterOnBoard() ? 45 : 22;
        } else if (isOnSpellCastEngine(def)) {
            value += 26;
        } else if (hasAbility(def, "PASSIVE", "REDUCE_LP_DAMAGE_TAKEN")
                || hasAbility(def, "ON_TURN_END", "HEAL_LP")) {
            value += 28;
        }
        if (evaluator.hasKeyword(room, ally, "GARDIEN") || evaluator.hasKeyword(room, ally, "SURVOL")) {
            value += 18;
        }
        // Extra weight if removing our last familiar breaks an active monster condition on board
        if (def.cardType() == CardType.FAMILIAR && bot.getAllFamiliars().size() == 1) {
            boolean dependedOn = bot.getAllMonsters().stream()
                    .anyMatch(m -> isAnyCondition(m.getDefinition(), "ALLY_FAMILIAR_PRESENT"));
            if (dependedOn) {
                value += 28;
            }
        }
        return value;
    }

    static int estimateCreaturePlayAtk(GameRoom room, PlayerState bot, CardDefinition def, BoardRuleEvaluator evaluator) {
        int atk = def.baseAtk() != null ? def.baseAtk() : 0;
        if ("SHADOW_M_01".equals(def.cardId()) || "FIRE_M_03".equals(def.cardId())) {
            if (!bot.getAllMonsters().isEmpty()) {
                atk += 1;
            }
        } else if ("FIRE_F_08".equals(def.cardId()) || "LIGHT_F_06".equals(def.cardId())) {
            if (!bot.getAllFamiliars().isEmpty()) {
                atk += 1;
            }
        } else if ("LIGHT_M_10".equals(def.cardId()) && !bot.getAllFamiliars().isEmpty()) {
            atk += 1;
        }
        return atk;
    }

    static int estimateCreaturePlayDef(GameRoom room, PlayerState bot, CardDefinition def) {
        int baseDef = def.baseDef() != null ? def.baseDef() : 0;
        int bonus = 0;
        if ("LIGHT_M_03".equals(def.cardId()) && !bot.getAllFamiliars().isEmpty()) {
            bonus += 1;
        } else if ("ICE_M_08".equals(def.cardId()) && !bot.getAllFamiliars().isEmpty()) {
            bonus += 2;
        }
        for (CardInstance allyMonster : bot.getAllMonsters()) {
            if ("LIGHT_M_09".equals(allyMonster.getDefinition().cardId())) {
                bonus += 1;
            }
        }
        for (CardInstance spell : bot.getAllSpellArtifacts()) {
            if (!spell.isFaceDown() && "LIGHT_S_05".equals(spell.getDefinition().cardId())
                    && def.cardType() == CardType.MONSTER) {
                bonus += 1;
            }
        }
        return baseDef + bonus;
    }

    static int maxVisibleEnemyAttack(GameRoom room, TeamId botTeam, BoardRuleEvaluator evaluator) {
        return room.getOpponentPlayers(botTeam).stream()
                .flatMap(p -> p.getAllBoardCreatures().stream())
                .filter(c -> !c.isSlowedCurrentTurn())
                .mapToInt(c -> evaluator.evaluateEffectiveAtk(room, c))
                .max()
                .orElse(0);
    }

    static int countVisibleOpponentFaceDownSpells(GameRoom room, TeamId botTeam) {
        return (int) room.getOpponentPlayers(botTeam).stream()
                .flatMap(p -> p.getAllSpellArtifacts().stream())
                .filter(CardInstance::isFaceDown)
                .count();
    }

    static int evaluateDebuffImpact(CardInstance target, int atkDelta, int defDelta, BoardRuleEvaluator evaluator, GameRoom room) {
        int curDef = evaluator.evaluateEffectiveDef(room, target);
        int curAtk = evaluator.evaluateEffectiveAtk(room, target);
        int score = 0;
        if (defDelta < 0) {
            if (curDef + defDelta <= 0) {
                score += 220;
            } else {
                score += (-defDelta) * 28;
            }
        }
        if (atkDelta < 0) {
            int effectiveReduction = Math.min(curAtk, -atkDelta);
            score += effectiveReduction * 24;
            if (target.isSlowedCurrentTurn() || target.getPosture() == Posture.DEFENSE && !evaluator.hasKeyword(room, target, "GARDIEN")) {
                score = (int) Math.round(score * 0.65);
            }
        }
        return score;
    }

    static boolean isDuplicateActiveCurse(PlayerState bot, String cardId) {
        return bot.getAllSpellArtifacts().stream()
                .anyMatch(s -> !s.isFaceDown() && Objects.equals(s.getDefinition().cardId(), cardId));
    }
}
