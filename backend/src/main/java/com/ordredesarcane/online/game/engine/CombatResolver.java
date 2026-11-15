package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.engine.AbilityInterpreter.AbilityContext;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.CombatOutcome;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CombatResolver {

    public static final String DIRECT_PLAYER_TARGET = "DIRECT_PLAYER";

    private final BoardRuleEvaluator evaluator;
    private final AbilityInterpreter abilities;

    public CombatResolver(BoardRuleEvaluator evaluator, AbilityInterpreter abilities) {
        this.evaluator = evaluator;
        this.abilities = abilities;
    }

    public void validateAttackLegality(GameRoom room, PlayerState attackerPlayer, CardInstance attacker, String targetId) {
        if (room.isTurn1TruceActive()) {
            throw new IllegalArgumentException("Trêve du Tour 1 : aucune attaque autorisée lors du 1er tour de table complet.");
        }
        if (!evaluator.canCreatureAttackNow(room, attacker)) {
            throw new IllegalArgumentException("Cette créature ne peut pas attaquer actuellement.");
        }

        TeamId opposingTeam = attackerPlayer.getTeamId().opponent();
        List<PlayerState> opponents = room.getOpponentPlayers(attackerPlayer.getTeamId());

        List<CardInstance> nonCamouflagedMonsters = new ArrayList<>();
        List<CardInstance> nonCamouflagedFamiliars = new ArrayList<>();
        List<CardInstance> nonCamouflagedGuardians = new ArrayList<>();

        for (PlayerState opp : opponents) {
            for (CardInstance monster : opp.getAllMonsters()) {
                if (!evaluator.hasKeyword(room, monster, "CAMOUFLAGE")) {
                    nonCamouflagedMonsters.add(monster);
                    if (evaluator.hasKeyword(room, monster, "GARDIEN")) {
                        nonCamouflagedGuardians.add(monster);
                    }
                }
            }
            for (CardInstance familiar : opp.getAllFamiliars()) {
                if (!evaluator.hasKeyword(room, familiar, "CAMOUFLAGE")) {
                    nonCamouflagedFamiliars.add(familiar);
                    if (evaluator.hasKeyword(room, familiar, "GARDIEN")) {
                        nonCamouflagedGuardians.add(familiar);
                    }
                }
            }
        }

        boolean hasSurvol = evaluator.hasKeyword(room, attacker, "SURVOL");

        if (DIRECT_PLAYER_TARGET.equals(targetId)) {
            if (!nonCamouflagedGuardians.isEmpty()) {
                throw new IllegalArgumentException("Un Gardien adverse oblige à le cibler en priorité.");
            }
            if (!nonCamouflagedMonsters.isEmpty()) {
                throw new IllegalArgumentException("Attaque directe bloquée par la Règle du Rempart (Monstre adverse présent).");
            }
            if (!nonCamouflagedFamiliars.isEmpty() && !hasSurvol) {
                throw new IllegalArgumentException("Attaque directe bloquée par la Règle du Rempart (Familier adverse non-camouflé présent).");
            }
            return;
        }

        CardInstance defender = room.findCreatureOnAnyBoard(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Cible d'attaque introuvable sur le terrain : " + targetId));
        PlayerState defOwner = room.getPlayer(defender.getOwnerPlayerId());
        if (defOwner == null || defOwner.getTeamId() != opposingTeam) {
            throw new IllegalArgumentException("Impossible d'attaquer une créature alliée.");
        }
        if (evaluator.hasKeyword(room, defender, "CAMOUFLAGE")) {
            throw new IllegalArgumentException("Cible protégée par Camouflage : impossible de l'attaquer.");
        }
        if (!nonCamouflagedGuardians.isEmpty() && !evaluator.hasKeyword(room, defender, "GARDIEN")) {
            throw new IllegalArgumentException("Règle Gardien : vous devez cibler la créature avec Gardien en priorité.");
        }
    }

    public boolean wouldDestroyAllyMonsterInCombat(GameRoom room, CardInstance attacker, CardInstance defender) {
        if (defender == null || defender.getDefinition().cardType() != CardType.MONSTER) {
            return false;
        }
        int atkA = evaluator.evaluateEffectiveAtk(room, attacker);
        if (defender.getPosture() == Posture.ATTACK) {
            return atkA >= evaluator.evaluateEffectiveAtk(room, defender);
        }
        return atkA > evaluator.evaluateEffectiveDef(room, defender);
    }

    public void resolveCombat(
            GameRoom room,
            PlayerState attackerPlayer,
            CardInstance attacker,
            String targetId,
            boolean directAttack) {
        attacker.incrementAttacksMadeThisTurn();
        String sourceZone = PlayerState.boardZoneOf(attacker);
        int sourceSlot = attacker.getSlot();
        Faction deckFaction = resolveProjectileFaction(attackerPlayer, attacker);

        if (directAttack) {
            TeamId defendingTeam = attackerPlayer.getTeamId().opponent();
            java.util.List<PlayerState> defendingPlayers = room.getTeamPlayers(defendingTeam);
            java.util.UUID targetPlayerId = defendingPlayers.isEmpty() ? null : defendingPlayers.getFirst().getUserId();
            boolean blocked = defendingPlayers.stream()
                    .anyMatch(PlayerState::isDirectAttacksBlockedThisTurn);
            if (blocked) {
                room.addLog(attackerPlayer.getUserId(), attackerPlayer.getUsername(), "DIRECT_BLOCKED",
                        "L'attaque directe de " + attacker.getDefinition().name() + " est bloquée par un mur magique !");
                room.recordVisualEvent(
                        deckFaction,
                        attackerPlayer.getUserId(),
                        attacker.getInstanceId(),
                        sourceZone,
                        sourceSlot,
                        targetPlayerId,
                        null,
                        null,
                        -1,
                        true,
                        Posture.DEFENSE,
                        CombatOutcome.DIRECT_BLOCKED,
                        0,
                        0);
                return;
            }
            int dmg = evaluator.evaluateEffectiveAtk(room, attacker);
            int defendingLpBefore = room.getTeam(defendingTeam).getLp();
            abilities.dealLpDamage(room, attackerPlayer, attacker.getDefinition().name(), defendingTeam, dmg);
            int actualDamage = Math.max(0, defendingLpBefore - room.getTeam(defendingTeam).getLp());
            room.recordVisualEvent(
                    deckFaction,
                    attackerPlayer.getUserId(),
                    attacker.getInstanceId(),
                    sourceZone,
                    sourceSlot,
                    targetPlayerId,
                    null,
                    null,
                    -1,
                    true,
                    Posture.ATTACK,
                    CombatOutcome.DIRECT_HIT,
                    actualDamage,
                    0);
            if (dmg > 0) {
                abilities.executeAbilitiesForTrigger(room, attackerPlayer, attacker, "ON_DESTROY_OR_DAMAGE_COMBAT",
                        false, AbilityContext.empty());
            }
            abilities.executeAbilitiesForTrigger(room, attackerPlayer, attacker, "ON_ATTACK_SURVIVE",
                    false, AbilityContext.empty());
            return;
        }

        CardInstance defender = room.findCreatureOnAnyBoard(targetId).orElse(null);
        if (defender == null) {
            room.addLog(attackerPlayer.getUserId(), attackerPlayer.getUsername(), "COMBAT_FIZZLE",
                    "La cible de " + attacker.getDefinition().name() + " a quitté le terrain.");
            return;
        }
        PlayerState defenderPlayer = room.getPlayer(defender.getOwnerPlayerId());
        String targetZone = PlayerState.boardZoneOf(defender);
        int targetSlot = defender.getSlot();
        Posture defenderPosture = defender.getPosture();

        abilities.executeAbilitiesForTrigger(room, defenderPlayer, defender, "ON_BEING_ATTACKED",
                false, new AbilityContext(null, null, null, false, GamePhase.COMBAT_PHASE, attacker, defender, null));

        int atkA = evaluator.evaluateEffectiveAtk(room, attacker);
        boolean attackerSurvived = true;
        boolean defenderDestroyed = false;
        boolean dealtLpDamage = false;
        int attackerTeamLpBefore = room.getTeam(attackerPlayer.getTeamId()).getLp();
        int defenderTeamLpBefore = room.getTeam(defenderPlayer.getTeamId()).getLp();

        if (defender.getPosture() == Posture.ATTACK) {
            int atkD = evaluator.evaluateEffectiveAtk(room, defender);
            room.addLog(attackerPlayer.getUserId(), attackerPlayer.getUsername(), "COMBAT",
                    attacker.getDefinition().name() + " (" + atkA + " ATQ) attaque "
                            + defender.getDefinition().name() + " (" + atkD + " ATQ - Attaque).");
            if (atkA > atkD) {
                defenderDestroyed = tryDestroyCombatCreature(room, defenderPlayer, defender);
                int excess = atkA - atkD;
                abilities.dealLpDamage(room, attackerPlayer, attacker.getDefinition().name(), defenderPlayer.getTeamId(), excess);
                dealtLpDamage = excess > 0;
            } else if (atkA == atkD) {
                defenderDestroyed = tryDestroyCombatCreature(room, defenderPlayer, defender);
                attackerSurvived = !tryDestroyCombatCreature(room, attackerPlayer, attacker);
            } else {
                attackerSurvived = !tryDestroyCombatCreature(room, attackerPlayer, attacker);
                int recoil = atkD - atkA;
                abilities.dealLpDamage(room, defenderPlayer, defender.getDefinition().name(), attackerPlayer.getTeamId(), recoil);
            }
        } else {
            int defD = evaluator.evaluateEffectiveDef(room, defender);
            room.addLog(attackerPlayer.getUserId(), attackerPlayer.getUsername(), "COMBAT",
                    attacker.getDefinition().name() + " (" + atkA + " ATQ) attaque "
                            + defender.getDefinition().name() + " (" + defD + " DEF - Défense).");
            if (atkA > defD) {
                defenderDestroyed = tryDestroyCombatCreature(room, defenderPlayer, defender);
            } else if (atkA < defD) {
                int recoil = defD - atkA;
                abilities.dealLpDamage(room, defenderPlayer, defender.getDefinition().name(), attackerPlayer.getTeamId(), recoil);
            }
        }

        int damageDealt = Math.max(0, defenderTeamLpBefore - room.getTeam(defenderPlayer.getTeamId()).getLp());
        int recoilDamage = Math.max(0, attackerTeamLpBefore - room.getTeam(attackerPlayer.getTeamId()).getLp());
        CombatOutcome outcome = resolveCombatOutcome(defenderPosture, attackerSurvived, defenderDestroyed);
        room.recordVisualEvent(
                deckFaction,
                attackerPlayer.getUserId(),
                attacker.getInstanceId(),
                sourceZone,
                sourceSlot,
                defenderPlayer.getUserId(),
                defender.getInstanceId(),
                targetZone,
                targetSlot,
                false,
                defenderPosture,
                outcome,
                damageDealt,
                recoilDamage);

        attacker.expireEndOfCombatModifiers();
        defender.expireEndOfCombatModifiers();

        if (defenderDestroyed && defender.getDefinition().cardType() == CardType.FAMILIAR) {
            abilities.executeAbilitiesForTrigger(room, attackerPlayer, attacker, "ON_DESTROY_ENEMY_FAMILIAR_COMBAT",
                    false, new AbilityContext(null, null, null, false, GamePhase.COMBAT_PHASE, attacker, defender, null));
        }
        if (defenderDestroyed || dealtLpDamage) {
            abilities.executeAbilitiesForTrigger(room, attackerPlayer, attacker, "ON_DESTROY_OR_DAMAGE_COMBAT",
                    false, new AbilityContext(null, null, null, false, GamePhase.COMBAT_PHASE, attacker, defender, null));
        }
        if (attackerSurvived) {
            abilities.executeAbilitiesForTrigger(room, attackerPlayer, attacker, "ON_ATTACK_SURVIVE",
                    false, AbilityContext.empty());
        }
    }

    private Faction resolveProjectileFaction(PlayerState attackerPlayer, CardInstance attacker) {
        if (attackerPlayer != null && attackerPlayer.getPrimaryFaction() != null) {
            return attackerPlayer.getPrimaryFaction();
        }
        return attacker != null && attacker.getDefinition() != null
                ? attacker.getDefinition().faction()
                : Faction.FIRE;
    }

    private CombatOutcome resolveCombatOutcome(
            Posture defenderPosture,
            boolean attackerSurvived,
            boolean defenderDestroyed) {
        if (defenderPosture == Posture.DEFENSE) {
            return defenderDestroyed ? CombatOutcome.DEFENSE_BROKEN : CombatOutcome.DEFENSE_BLOCKED;
        }
        if (defenderDestroyed && !attackerSurvived) {
            return CombatOutcome.TRADE;
        }
        if (defenderDestroyed) {
            return CombatOutcome.ATTACKER_VICTORY;
        }
        if (!attackerSurvived) {
            return CombatOutcome.ATTACKER_REPELLED;
        }
        return CombatOutcome.DEFENSE_BLOCKED;
    }

    private boolean tryDestroyCombatCreature(GameRoom room, PlayerState owner, CardInstance target) {
        if (target.isPreventDestructionThisCombat()) {
            room.addLog(owner.getUserId(), owner.getUsername(), "DESTROY_PREVENTED",
                    target.getDefinition().name() + " est sauvé de la destruction !");
            return false;
        }
        return owner.removeFromBoard(target.getInstanceId());
    }
}
