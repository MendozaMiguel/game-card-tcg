package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.StatModifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BoardRuleEvaluator {

    public int evaluateEffectiveAtk(GameRoom room, CardInstance creature) {
        if (creature.getDefinition().baseAtk() == null) {
            return 0;
        }
        int total = creature.getDefinition().baseAtk();
        for (StatModifier mod : creature.getModifiers()) {
            total += mod.getAtkDelta();
        }
        total += computePassiveDelta(room, creature, true);
        return Math.max(0, total);
    }

    public int evaluateEffectiveDef(GameRoom room, CardInstance creature) {
        if (creature.getDefinition().baseDef() == null) {
            return 0;
        }
        int nonOpponentSpellDef = creature.getDefinition().baseDef();
        int opponentSpellDefDelta = 0;

        for (StatModifier mod : creature.getModifiers()) {
            if (mod.isFromOpponentSpell()) {
                opponentSpellDefDelta += mod.getDefDelta();
            } else {
                nonOpponentSpellDef += mod.getDefDelta();
            }
        }

        PassiveSplit passive = computePassiveDefSplit(room, creature);
        nonOpponentSpellDef += passive.nonOpponentSpellDelta();
        opponentSpellDefDelta += passive.opponentSpellDelta();

        if (hasSpellDestructionImmunity(room, creature) && nonOpponentSpellDef > 0 && opponentSpellDefDelta < 0) {
            return Math.max(1, nonOpponentSpellDef + opponentSpellDefDelta);
        }
        return Math.max(0, nonOpponentSpellDef + opponentSpellDefDelta);
    }

    public boolean hasKeyword(GameRoom room, CardInstance creature, String keyword) {
        if ("CAMOUFLAGE".equals(keyword)) {
            return creature.isCamouflaged();
        }
        if ("CHARGE".equals(keyword) && isChargeStripped(room, creature)) {
            return false;
        }
        return creature.getDefinition().keywords().contains(keyword);
    }

    public List<String> evaluateKeywords(GameRoom room, CardInstance creature) {
        Set<String> result = new LinkedHashSet<>(creature.getDefinition().keywords());
        if (creature.isCamouflaged()) {
            result.add("CAMOUFLAGE");
        } else {
            result.remove("CAMOUFLAGE");
        }
        if (isChargeStripped(room, creature)) {
            result.remove("CHARGE");
        }
        return List.copyOf(result);
    }

    public boolean isChargeStripped(GameRoom room, CardInstance creature) {
        PlayerState owner = room.getPlayer(creature.getOwnerPlayerId());
        if (owner == null || creature.getDefinition().cardType() != CardType.MONSTER) {
            return false;
        }
        for (PlayerState opponent : room.getOpponentPlayers(owner.getTeamId())) {
            for (CardInstance spell : opponent.getAllSpellArtifacts()) {
                if (spell.isFaceDown()) {
                    continue;
                }
                for (AbilityDefinition ability : spell.getDefinition().abilitiesScript()) {
                    if ("PASSIVE".equals(ability.trigger())
                            && "STRIP_CHARGE".equals(ability.action())
                            && checkCondition(room, opponent, spell, ability.condition(), null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean hasSpellDestructionImmunity(GameRoom room, CardInstance creature) {
        for (AbilityDefinition ability : creature.getDefinition().abilitiesScript()) {
            if ("PASSIVE".equals(ability.trigger())
                    && "SPELL_DESTRUCTION_IMMUNITY".equals(ability.action())
                    && checkCondition(room, room.getPlayer(creature.getOwnerPlayerId()), creature, ability.condition(), null)) {
                return true;
            }
        }
        return false;
    }

    public int evaluateTeamLpDamageReduction(GameRoom room, com.ordredesarcane.online.game.model.TeamId teamId) {
        int reduction = 0;
        for (PlayerState player : room.getTeamPlayers(teamId)) {
            for (CardInstance creature : player.getAllBoardCreatures()) {
                for (AbilityDefinition ability : creature.getDefinition().abilitiesScript()) {
                    if ("PASSIVE".equals(ability.trigger())
                            && "REDUCE_LP_DAMAGE_TAKEN".equals(ability.action())
                            && ability.value() != null
                            && checkCondition(room, player, creature, ability.condition(), null)) {
                        reduction += ability.value();
                    }
                }
            }
        }
        return Math.max(0, reduction);
    }

    public int getMaxAttacksPerTurn(GameRoom room, CardInstance creature) {
        int attacks = 1;
        PlayerState owner = room.getPlayer(creature.getOwnerPlayerId());
        if (owner == null) {
            return attacks;
        }
        for (AbilityDefinition ability : creature.getDefinition().abilitiesScript()) {
            if ("PASSIVE".equals(ability.trigger())
                    && "ALLOW_EXTRA_ATTACK".equals(ability.action())
                    && ability.value() != null
                    && checkCondition(room, owner, creature, ability.condition(), null)) {
                attacks += ability.value();
            }
        }
        return attacks;
    }

    public boolean canCreatureAttackNow(GameRoom room, CardInstance creature) {
        if (room.isFinished() || room.isTurn1TruceActive()) {
            return false;
        }
        if (room.getPhase() != GamePhase.MAIN_PHASE && room.getPhase() != GamePhase.COMBAT_PHASE) {
            return false;
        }
        PlayerState active = room.getActivePlayer();
        if (active == null || !active.getUserId().equals(creature.getOwnerPlayerId())) {
            return false;
        }
        if (creature.getDefinition().cardType().isSpellCategory() || creature.getSlot() < 0) {
            return false;
        }
        return creature.getPosture() == Posture.ATTACK
                && !creature.isSlowedCurrentTurn()
                && creature.getAttacksMadeThisTurn() < getMaxAttacksPerTurn(room, creature);
    }

    public boolean checkCondition(
            GameRoom room,
            PlayerState owner,
            CardInstance source,
            String condition,
            CardInstance optionalTarget) {
        if (condition == null || "NONE".equals(condition) || "PERSISTENT_2_TURNS".equals(condition)
                || "PERSISTENT_CURSE".equals(condition)) {
            return true;
        }
        if (owner == null) {
            return false;
        }
        return switch (condition) {
            case "OPPONENT_HAS_FAMILIAR" -> room.getOpponentPlayers(owner.getTeamId()).stream()
                    .anyMatch(opp -> !opp.getAllFamiliars().isEmpty());
            case "ALLY_OTHER_MONSTER" -> source != null && owner.getAllMonsters().stream()
                    .anyMatch(m -> !m.getInstanceId().equals(source.getInstanceId()));
            case "ALLY_OTHER_FAMILIAR" -> source != null && owner.getAllFamiliars().stream()
                    .anyMatch(f -> !f.getInstanceId().equals(source.getInstanceId()));
            case "ALLY_EPIC_MONSTER_PRESENT" -> owner.hasEpicMonsterOnBoard();
            case "ALLY_FAMILIAR_PRESENT" -> !owner.getAllFamiliars().isEmpty();
            case "ALLY_MONSTER_PRESENT" -> !owner.getAllMonsters().isEmpty();
            case "IN_DEFENSE_POSTURE" -> source != null && source.getPosture() == Posture.DEFENSE;
            case "ON_OPPONENT_TURN" -> room.getActivePlayer() != null
                    && room.getActivePlayer().getTeamId() != owner.getTeamId();
            case "ANY_FAMILIAR_ON_FIELD" -> room.getPlayers().values().stream()
                    .anyMatch(p -> !p.getAllFamiliars().isEmpty());
            case "HEALED_THIS_TURN" -> owner.isHealedThisTurn();
            case "TARGET_IS_MONSTER" -> optionalTarget != null
                    && optionalTarget.getDefinition().cardType() == CardType.MONSTER;
            default -> false;
        };
    }

    public List<CardInstance> cleanupZeroDefCreatures(GameRoom room) {
        List<CardInstance> destroyed = new ArrayList<>();
        boolean removedAny;
        do {
            removedAny = false;
            for (PlayerState player : room.getPlayers().values()) {
                for (CardInstance creature : new ArrayList<>(player.getAllBoardCreatures())) {
                    int def = evaluateEffectiveDef(room, creature);
                    if (def <= 0) {
                        player.removeFromBoard(creature.getInstanceId());
                        destroyed.add(creature);
                        room.addLog(
                                player.getUserId(),
                                player.getUsername(),
                                "CREATURE_DESTROYED_0_DEF",
                                creature.getDefinition().name() + " tombe à 0 DEF et est détruit !");
                        removedAny = true;
                    }
                }
            }
        } while (removedAny);
        return destroyed;
    }

    private int computePassiveDelta(GameRoom room, CardInstance target, boolean atk) {
        PlayerState targetOwner = room.getPlayer(target.getOwnerPlayerId());
        if (targetOwner == null) {
            return 0;
        }
        int delta = 0;

        for (PlayerState owner : room.getPlayers().values()) {
            boolean samePlayer = owner.getUserId().equals(targetOwner.getUserId());
            boolean opposingTeam = owner.getTeamId() != targetOwner.getTeamId();

            List<CardInstance> passiveSources = new ArrayList<>(owner.getAllBoardCreatures());
            for (CardInstance spell : owner.getAllSpellArtifacts()) {
                if (!spell.isFaceDown()) {
                    passiveSources.add(spell);
                }
            }

            for (CardInstance source : passiveSources) {
                for (AbilityDefinition ability : source.getDefinition().abilitiesScript()) {
                    if (!"PASSIVE".equals(ability.trigger())) {
                        continue;
                    }
                    boolean applies = passiveAppliesToTarget(ability.target(), target, source, samePlayer, opposingTeam);
                    if (!applies || !checkCondition(room, owner, source, ability.condition(), null)) {
                        continue;
                    }
                    if (atk && ("MODIFY_ATK".equals(ability.action()) || "MODIFY_ATK_AND_DEF".equals(ability.action()))) {
                        if (ability.value() != null) {
                            delta += ability.value();
                        }
                    } else if (!atk && "MODIFY_DEF".equals(ability.action())) {
                        if (ability.value() != null) {
                            delta += ability.value();
                        }
                    } else if (!atk && "MODIFY_ATK_AND_DEF".equals(ability.action())) {
                        if (ability.secondaryValue() != null) {
                            delta += ability.secondaryValue();
                        }
                    }
                }
            }
        }
        return delta;
    }

    private PassiveSplit computePassiveDefSplit(GameRoom room, CardInstance target) {
        PlayerState targetOwner = room.getPlayer(target.getOwnerPlayerId());
        if (targetOwner == null) {
            return new PassiveSplit(0, 0);
        }
        int nonOpponentSpell = 0;
        int opponentSpell = 0;

        for (PlayerState owner : room.getPlayers().values()) {
            boolean samePlayer = owner.getUserId().equals(targetOwner.getUserId());
            boolean opposingTeam = owner.getTeamId() != targetOwner.getTeamId();

            List<CardInstance> passiveSources = new ArrayList<>(owner.getAllBoardCreatures());
            for (CardInstance spell : owner.getAllSpellArtifacts()) {
                if (!spell.isFaceDown()) {
                    passiveSources.add(spell);
                }
            }

            for (CardInstance source : passiveSources) {
                boolean isOppSpell = opposingTeam && source.getDefinition().cardType().isSpellCategory();
                for (AbilityDefinition ability : source.getDefinition().abilitiesScript()) {
                    if (!"PASSIVE".equals(ability.trigger())) {
                        continue;
                    }
                    if (!passiveAppliesToTarget(ability.target(), target, source, samePlayer, opposingTeam)) {
                        continue;
                    }
                    if (!checkCondition(room, owner, source, ability.condition(), null)) {
                        continue;
                    }
                    int val = 0;
                    if ("MODIFY_DEF".equals(ability.action()) && ability.value() != null) {
                        val = ability.value();
                    } else if ("MODIFY_ATK_AND_DEF".equals(ability.action()) && ability.secondaryValue() != null) {
                        val = ability.secondaryValue();
                    }
                    if (isOppSpell) {
                        opponentSpell += val;
                    } else {
                        nonOpponentSpell += val;
                    }
                }
            }
        }
        return new PassiveSplit(nonOpponentSpell, opponentSpell);
    }

    private boolean passiveAppliesToTarget(
            String abilityTarget,
            CardInstance target,
            CardInstance source,
            boolean samePlayer,
            boolean opposingTeam) {
        if (abilityTarget == null) {
            return false;
        }
        CardDefinition targetDef = target.getDefinition();
        return switch (abilityTarget) {
            case "SELF" -> source.getInstanceId().equals(target.getInstanceId());
            case "ALL_ALLY_MONSTERS" -> samePlayer && targetDef.cardType() == CardType.MONSTER;
            case "ALL_ALLY_CREATURES" -> samePlayer && !targetDef.cardType().isSpellCategory();
            case "ALL_OPPONENT_MONSTERS" -> opposingTeam && targetDef.cardType() == CardType.MONSTER;
            default -> false;
        };
    }

    private record PassiveSplit(int nonOpponentSpellDelta, int opponentSpellDelta) {
    }
}
