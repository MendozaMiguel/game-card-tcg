package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.AbilityOption;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.CombatOutcome;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.ModifierDuration;
import com.ordredesarcane.online.game.model.PendingModalChoice;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.StatModifier;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.game.model.TeamState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AbilityInterpreter {

    private final BoardRuleEvaluator evaluator;

    public AbilityInterpreter(BoardRuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public AbilityOutcome executeAbilitiesForTrigger(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String trigger,
            boolean strictTargeting,
            AbilityContext context) {
        List<AbilityDefinition> matching = source.getDefinition().abilitiesScript().stream()
                .filter(a -> trigger.equals(a.trigger()))
                .toList();
        if (matching.isEmpty()) {
            return AbilityOutcome.completed();
        }

        for (AbilityDefinition ability : matching) {
            if (!"REACTIVE".equals(trigger)
                    && !evaluator.checkCondition(room, actor, source, ability.condition(), context.combatTarget())) {
                continue;
            }
            if ("MODAL_CHOICE".equals(ability.action())) {
                List<AbilityOption> options = ability.options();
                if (options == null || options.isEmpty()) {
                    continue;
                }
                if (context.choiceIndex() == null) {
                    if (context.deferChoiceIfMissing()) {
                        room.setPendingChoice(new PendingModalChoice(
                                actor.getUserId(),
                                source.getInstanceId(),
                                source.getDefinition().name(),
                                context.returnPhase(),
                                options,
                                context.targetId()));
                        room.setPhase(GamePhase.WAITING_FOR_MODAL_CHOICE);
                        return AbilityOutcome.deferredChoice();
                    }
                }
                int chosenIdx = context.choiceIndex() == null ? 0 : context.choiceIndex();
                if (chosenIdx < 0 || chosenIdx >= options.size()) {
                    throw new IllegalArgumentException("Choix d'effet invalide : " + chosenIdx);
                }
                AbilityOption option = options.get(chosenIdx);
                executeAction(
                        room,
                        actor,
                        source,
                        option.action(),
                        option.target(),
                        option.value(),
                        null,
                        option.duration(),
                        strictTargeting,
                        context);
            } else {
                executeAction(
                        room,
                        actor,
                        source,
                        ability.action(),
                        ability.target(),
                        ability.value(),
                        ability.secondaryValue(),
                        ability.duration(),
                        strictTargeting,
                        context);
            }
        }
        return AbilityOutcome.completed();
    }

    public boolean hasValidTargetForSpellPlay(GameRoom room, PlayerState actor, CardInstance spellCard, Integer choiceIndex) {
        for (AbilityDefinition ability : spellCard.getDefinition().abilitiesScript()) {
            if (!"ON_PLAY".equals(ability.trigger())) {
                continue;
            }
            if ("MODAL_CHOICE".equals(ability.action())) {
                if (choiceIndex != null && ability.options() != null && choiceIndex >= 0 && choiceIndex < ability.options().size()) {
                    if (hasAtLeastOneCandidate(room, actor, spellCard, ability.options().get(choiceIndex).target(), ability.options().get(choiceIndex).action())) {
                        return true;
                    }
                    return false;
                }
                if (ability.options() != null) {
                    return ability.options().stream()
                            .anyMatch(opt -> hasAtLeastOneCandidate(room, actor, spellCard, opt.target(), opt.action()));
                }
            } else if (!hasAtLeastOneCandidate(room, actor, spellCard, ability.target(), ability.action())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAtLeastOneCandidate(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String targetSpec,
            String action) {
        if (targetSpec == null || "NONE".equals(targetSpec) || "SELF".equals(targetSpec)
                || "OWNER".equals(targetSpec) || "OWNER_TEAM".equals(targetSpec)
                || "ALLY_OR_OWNER_PLAYER".equals(targetSpec) || "OPPONENT_PLAYER".equals(targetSpec)
                || "ALL_OPPONENT_MONSTERS".equals(targetSpec) || "ALL_OPPONENT_FAMILIARS".equals(targetSpec)
                || "ALL_OPPONENT_CREATURES".equals(targetSpec) || "ALL_ALLY_MONSTERS".equals(targetSpec)
                || "ALL_ALLY_CREATURES".equals(targetSpec)
                || "MODIFY_DEF_OR_LP_IF_EMPTY".equals(action)) {
            return true;
        }
        return !findTargetCandidates(room, actor, source, targetSpec).isEmpty();
    }

    private void executeAction(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String action,
            String targetSpec,
            Integer value,
            Integer secondaryValue,
            String durationStr,
            boolean strictTargeting,
            AbilityContext context) {
        boolean fromOpponentSpell = source.getDefinition().cardType().isSpellCategory();
        ModifierDuration duration = parseDuration(durationStr);

        switch (action) {
            case "HEAL_LP" -> {
                int heal = value != null ? value : 0;
                TeamState team = room.getTeam(actor.getTeamId());
                int healed = team.heal(heal);
                actor.setHealedThisTurn(true);
                room.addLog(actor.getUserId(), actor.getUsername(), "HEAL",
                        source.getDefinition().name() + " : +" + healed + " PV (" + team.getLp() + " PV).");
            }
            case "HEAL_OR_GAIN_ENERGY_IF_LP_GE_20" -> {
                TeamState team = room.getTeam(actor.getTeamId());
                if (team.getLp() >= 20) {
                    actor.addEnergy(1);
                    room.addLog(actor.getUserId(), actor.getUsername(), "GAIN_ENERGY",
                            source.getDefinition().name() + " : PV ≥ 20, +1 jeton d'Énergie.");
                } else {
                    int healed = team.heal(value != null ? value : 1);
                    actor.setHealedThisTurn(true);
                    room.addLog(actor.getUserId(), actor.getUsername(), "HEAL",
                            source.getDefinition().name() + " : +" + healed + " PV.");
                }
            }
            case "DAMAGE_LP" -> {
                TeamId targetTeamId = resolveDamageTargetTeam(room, actor, targetSpec, context);
                int lpBefore = room.getTeam(targetTeamId).getLp();
                dealLpDamage(room, actor, source.getDefinition().name(), targetTeamId, value != null ? value : 0);
                int dealt = Math.max(0, lpBefore - room.getTeam(targetTeamId).getLp());
                if (targetTeamId != actor.getTeamId()) {
                    List<PlayerState> victims = room.getTeamPlayers(targetTeamId);
                    UUID targetPlayerId = victims.isEmpty() ? null : victims.getFirst().getUserId();
                    Faction projFaction = actor.getPrimaryFaction() != null
                            ? actor.getPrimaryFaction()
                            : source.getDefinition().faction();
                    room.recordVisualEvent(
                            projFaction,
                            actor.getUserId(),
                            source.getInstanceId(),
                            PlayerState.boardZoneOf(source),
                            source.getSlot(),
                            targetPlayerId,
                            null,
                            null,
                            -1,
                            true,
                            Posture.ATTACK,
                            CombatOutcome.SPELL_STRIKE,
                            dealt,
                            0);
                }
            }
            case "GAIN_ENERGY" -> {
                int gain = value != null ? value : 1;
                actor.addEnergy(gain);
                room.addLog(actor.getUserId(), actor.getUsername(), "GAIN_ENERGY",
                        source.getDefinition().name() + " : +" + gain + " jeton(s) d'Énergie.");
            }
            case "BLOOD_PACT_ENERGY" -> {
                TeamState team = room.getTeam(actor.getTeamId());
                team.applyDamage(1);
                actor.addEnergy(1);
                room.addLog(actor.getUserId(), actor.getUsername(), "BLOOD_PACT",
                        source.getDefinition().name() + " : -1 PV pour +1 jeton d'Énergie.");
            }
            case "DRAW_CARD" -> {
                int count = value != null ? value : 1;
                for (int i = 0; i < count; i++) {
                    if (context.drawCallback() != null) {
                        context.drawCallback().drawOneOrDeckOut(room, actor);
                    }
                }
            }
            case "SACRIFICE_SELF" -> {
                actor.removeFromBoard(source.getInstanceId());
                room.addLog(actor.getUserId(), actor.getUsername(), "SACRIFICE",
                        source.getDefinition().name() + " est sacrifié.");
            }
            case "PEEK_OPPONENT_TOP_CARD" -> {
                PlayerState opponent = resolveTargetOpponentPlayer(room, actor, context.targetId());
                peekTopCard(room, actor, opponent, source.getDefinition().name());
            }
            case "PEEK_OPPONENT_TOP_CARD_BUFF_IF_SPELL" -> {
                PlayerState opponent = resolveTargetOpponentPlayer(room, actor, context.targetId());
                CardInstance top = peekTopCard(room, actor, opponent, source.getDefinition().name());
                if (top != null && top.getDefinition().cardType().isSpellCategory()) {
                    List<CardInstance> allies = actor.getAllMonsters();
                    if (!allies.isEmpty()) {
                        CardInstance chosen = resolveAllyFromIdOrFirst(allies, context.secondaryTargetId(), context.targetId());
                        chosen.addModifier(new StatModifier(
                                source.getDefinition().cardId(),
                                actor.getTeamId(),
                                0,
                                value != null ? value : 1,
                                duration,
                                false,
                                false));
                        room.addLog(actor.getUserId(), actor.getUsername(), "BUFF",
                                "Carte adverse est un Sort : " + chosen.getDefinition().name() + " gagne +" + (value != null ? value : 1) + " DEF.");
                    }
                }
            }
            case "LOCK_OPPONENT_SPELLS" -> {
                for (PlayerState opponent : room.getOpponentPlayers(actor.getTeamId())) {
                    opponent.setSpellsLockedNextTurn(true);
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "SPELL_LOCK",
                        source.getDefinition().name() + " : Sorts adverses verrouillés au prochain tour !");
            }
            case "BLOCK_DIRECT_ATTACKS" -> {
                for (PlayerState ally : room.getTeamPlayers(actor.getTeamId())) {
                    ally.setDirectAttacksBlockedThisTurn(true);
                }
                if (room.getPendingReaction() != null && room.getPendingReaction().isDirectAttack()) {
                    room.getPendingReaction().setActionCancelled(true);
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "BLOCK_DIRECT",
                        source.getDefinition().name() + " : attaques directes bloquées pour ce tour.");
            }
            case "CANCEL_ATTACK" -> {
                if (room.getPendingReaction() != null) {
                    room.getPendingReaction().setActionCancelled(true);
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "CANCEL_ATTACK",
                        source.getDefinition().name() + " annule l'attaque !");
            }
            case "CANCEL_AND_EXHAUST" -> {
                if (room.getPendingReaction() != null) {
                    room.getPendingReaction().setActionCancelled(true);
                    String famId = room.getPendingReaction().getSourceCardInstanceId();
                    if (famId != null) {
                        room.findCreatureOnAnyBoard(famId).ifPresent(f -> f.setExhausted(true));
                    }
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "CANCEL_FAMILIAR",
                        source.getDefinition().name() + " annule et épuise le Familier adverse !");
            }
            case "REDIRECT_ATTACK_TO_SELF" -> {
                if (room.getPendingReaction() != null) {
                    room.getPendingReaction().setDefenderTargetId(source.getInstanceId());
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "REDIRECT",
                        source.getDefinition().name() + " redirige l'attaque sur elle-même !");
            }
            case "PREVENT_DESTRUCTION" -> {
                List<CardInstance> allies = resolveTargets(room, actor, source, targetSpec, false, context);
                for (CardInstance ally : allies) {
                    ally.setPreventDestructionThisCombat(true);
                    room.addLog(actor.getUserId(), actor.getUsername(), "SHIELD",
                            source.getDefinition().name() + " empêche la destruction de " + ally.getDefinition().name() + ".");
                }
            }
            case "MODIFY_ATK" -> applyStatMod(room, actor, source, targetSpec, value != null ? value : 0, 0, duration, fromOpponentSpell, strictTargeting, context);
            case "MODIFY_DEF" -> applyStatMod(room, actor, source, targetSpec, 0, value != null ? value : 0, duration, fromOpponentSpell, strictTargeting, context);
            case "MODIFY_ATK_AND_DEF" -> applyStatMod(
                    room,
                    actor,
                    source,
                    targetSpec,
                    value != null ? value : 0,
                    secondaryValue != null ? secondaryValue : 0,
                    duration,
                    fromOpponentSpell,
                    strictTargeting,
                    context);
            case "MODIFY_DEF_OR_LP_IF_EMPTY" -> {
                List<CardInstance> monsters = findTargetCandidates(room, actor, source, "OPPONENT_MONSTER");
                if (monsters.isEmpty()) {
                    dealLpDamage(room, actor, source.getDefinition().name(), actor.getTeamId().opponent(),
                            secondaryValue != null ? secondaryValue : 2);
                } else {
                    applyStatMod(room, actor, source, "OPPONENT_MONSTER", 0, value != null ? value : -2,
                            duration, fromOpponentSpell, strictTargeting, context);
                }
            }
            case "GRANT_CAMOUFLAGE" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
                for (CardInstance target : targets) {
                    boolean firstTime = !target.isFirstCamouflageUsed();
                    target.setCamouflageUntilNextOwnTurn(true);
                    target.setFirstCamouflageUsed(true);
                    room.addLog(actor.getUserId(), actor.getUsername(), "CAMOUFLAGE",
                            target.getDefinition().name() + " gagne Camouflage.");
                    if (firstTime) {
                        executeAbilitiesForTrigger(room, room.getPlayer(target.getOwnerPlayerId()), target,
                                "ON_FIRST_CAMOUFLAGE", false, AbilityContext.empty());
                    }
                    if (room.getPendingReaction() != null
                            && target.getInstanceId().equals(room.getPendingReaction().getDefenderTargetId())) {
                        room.getPendingReaction().setActionCancelled(true);
                    }
                    if (room.getPendingReaction() != null
                            && target.getInstanceId().equals(room.getPendingReaction().getPendingTargetId())) {
                        room.getPendingReaction().setActionCancelled(true);
                    }
                }
            }
            case "APPLY_SLOW" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
                UUID activePlayerId = room.getActivePlayer() != null ? room.getActivePlayer().getUserId() : null;
                for (CardInstance target : targets) {
                    boolean currentlyTargetsOwnTurn = target.getOwnerPlayerId().equals(activePlayerId);
                    target.applySlow(currentlyTargetsOwnTurn);
                    room.addLog(actor.getUserId(), actor.getUsername(), "SLOW",
                            target.getDefinition().name() + " est Ralenti.");
                }
            }
            case "EXHAUST_TARGET" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
                for (CardInstance target : targets) {
                    target.setExhausted(true);
                    target.setActivatedThisTurn(true);
                    room.addLog(actor.getUserId(), actor.getUsername(), "EXHAUST",
                            target.getDefinition().name() + " est Épuisé.");
                }
            }
            case "CLEANSE_DEBUFFS" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
                for (CardInstance target : targets) {
                    target.clearDebuffs();
                    room.addLog(actor.getUserId(), actor.getUsername(), "CLEANSE",
                            target.getDefinition().name() + " est purifié de tous ses malus.");
                }
            }
            case "DESTROY_TARGET", "DESTROY_ALL" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
                Faction projFaction = actor.getPrimaryFaction() != null
                        ? actor.getPrimaryFaction()
                        : source.getDefinition().faction();
                for (CardInstance target : targets) {
                    PlayerState owner = room.getPlayer(target.getOwnerPlayerId());
                    String targetZone = PlayerState.boardZoneOf(target);
                    int targetSlot = target.getSlot();
                    Posture targetPosture = target.getPosture();
                    if (fromOpponentSpell && evaluator.hasSpellDestructionImmunity(room, target)) {
                        room.addLog(actor.getUserId(), actor.getUsername(), "SPELL_IMMUNE",
                                target.getDefinition().name() + " est immunisé aux destructions par Sort en Défense.");
                        if (owner != null && owner.getTeamId() != actor.getTeamId()) {
                            room.recordVisualEvent(
                                    projFaction,
                                    actor.getUserId(),
                                    source.getInstanceId(),
                                    PlayerState.boardZoneOf(source),
                                    source.getSlot(),
                                    owner.getUserId(),
                                    target.getInstanceId(),
                                    targetZone,
                                    targetSlot,
                                    false,
                                    targetPosture,
                                    CombatOutcome.DEFENSE_BLOCKED,
                                    0,
                                    0);
                        }
                        continue;
                    }
                    if (owner != null && owner.removeFromBoard(target.getInstanceId())) {
                        room.addLog(actor.getUserId(), actor.getUsername(), "DESTROY",
                                source.getDefinition().name() + " détruit " + target.getDefinition().name() + ".");
                        if (owner.getTeamId() != actor.getTeamId()) {
                            room.recordVisualEvent(
                                    projFaction,
                                    actor.getUserId(),
                                    source.getInstanceId(),
                                    PlayerState.boardZoneOf(source),
                                    source.getSlot(),
                                    owner.getUserId(),
                                    target.getInstanceId(),
                                    targetZone,
                                    targetSlot,
                                    false,
                                    targetPosture,
                                    CombatOutcome.SPELL_STRIKE,
                                    0,
                                    0);
                        }
                    }
                }
            }
            case "FORCE_DEFENSE_POSTURE" -> {
                List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, false, context);
                for (CardInstance target : targets) {
                    target.setPosture(Posture.DEFENSE);
                }
                room.addLog(actor.getUserId(), actor.getUsername(), "FORCE_DEFENSE",
                        source.getDefinition().name() + " passe les Monstres adverses survivants en Position de Défense.");
            }
            default -> throw new IllegalStateException("Action d'effet non supportée : " + action);
        }
    }

    private void applyStatMod(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String targetSpec,
            int atkDelta,
            int defDelta,
            ModifierDuration duration,
            boolean fromOpponentSpell,
            boolean strictTargeting,
            AbilityContext context) {
        List<CardInstance> targets = resolveTargets(room, actor, source, targetSpec, strictTargeting, context);
        boolean isDebuff = atkDelta < 0 || defDelta < 0;
        for (CardInstance target : targets) {
            boolean opponentTargeting = !target.getOwnerPlayerId().equals(actor.getUserId());
            target.addModifier(new StatModifier(
                    source.getDefinition().cardId(),
                    actor.getTeamId(),
                    atkDelta,
                    defDelta,
                    duration,
                    isDebuff,
                    fromOpponentSpell && opponentTargeting));
            room.addLog(actor.getUserId(), actor.getUsername(), "STAT_MOD",
                    target.getDefinition().name() + " : "
                            + (atkDelta != 0 ? (atkDelta > 0 ? "+" : "") + atkDelta + " ATQ " : "")
                            + (defDelta != 0 ? (defDelta > 0 ? "+" : "") + defDelta + " DEF" : ""));
        }
    }

    public void dealLpDamage(GameRoom room, PlayerState sourcePlayer, String sourceName, TeamId targetTeamId, int rawDamage) {
        int reduction = evaluator.evaluateTeamLpDamageReduction(room, targetTeamId);
        int finalDamage = Math.max(0, rawDamage - reduction);
        TeamState victimTeam = room.getTeam(targetTeamId);
        victimTeam.applyDamage(finalDamage);
        room.addLog(sourcePlayer != null ? sourcePlayer.getUserId() : null,
                sourcePlayer != null ? sourcePlayer.getUsername() : "Système",
                "LP_DAMAGE",
                sourceName + " inflige " + finalDamage + " dégât(s) aux PV de " + targetTeamId
                        + (reduction > 0 ? " (réduit de " + reduction + ")" : "")
                        + " -> " + victimTeam.getLp() + " PV restants.");
    }

    private CardInstance peekTopCard(GameRoom room, PlayerState actor, PlayerState opponent, String sourceName) {
        if (opponent == null || opponent.getDeck().isEmpty()) {
            room.addLog(actor.getUserId(), actor.getUsername(), "SCOUT_EMPTY",
                    sourceName + " : le deck adverse est vide.");
            return null;
        }
        CardInstance top = opponent.getDeck().getFirst();
        actor.getPeekedOpponentTopCards().put(opponent.getUserId(), top.getDefinition().cardId());
        room.addLog(actor.getUserId(), actor.getUsername(), "SCOUT",
                actor.getUsername() + " consulte discrètement la carte du dessus du deck de " + opponent.getUsername() + ".");
        return top;
    }

    private PlayerState resolveTargetOpponentPlayer(GameRoom room, PlayerState actor, String targetId) {
        List<PlayerState> opponents = room.getOpponentPlayers(actor.getTeamId());
        if (targetId != null && !targetId.isBlank()) {
            for (PlayerState opp : opponents) {
                if (opp.getUserId().toString().equals(targetId)) {
                    return opp;
                }
            }
        }
        return opponents.isEmpty() ? null : opponents.getFirst();
    }

    private CardInstance resolveAllyFromIdOrFirst(List<CardInstance> allies, String preferredId, String fallbackId) {
        for (CardInstance ally : allies) {
            if (ally.getInstanceId().equals(preferredId) || ally.getInstanceId().equals(fallbackId)) {
                return ally;
            }
        }
        return allies.getFirst();
    }

    private TeamId resolveDamageTargetTeam(GameRoom room, PlayerState actor, String targetSpec, AbilityContext context) {
        if ("ATTACKER_OWNER".equals(targetSpec) && context.combatAttacker() != null) {
            PlayerState p = room.getPlayer(context.combatAttacker().getOwnerPlayerId());
            return p != null ? p.getTeamId() : actor.getTeamId().opponent();
        }
        if ("VICTIM_OWNER".equals(targetSpec) && context.combatTarget() != null) {
            PlayerState p = room.getPlayer(context.combatTarget().getOwnerPlayerId());
            return p != null ? p.getTeamId() : actor.getTeamId().opponent();
        }
        return actor.getTeamId().opponent();
    }

    public List<CardInstance> resolveTargets(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String targetSpec,
            boolean strictTargeting,
            AbilityContext context) {
        if (targetSpec == null || "NONE".equals(targetSpec)) {
            return List.of();
        }
        switch (targetSpec) {
            case "SELF" -> {
                return List.of(source);
            }
            case "ATTACKING_MONSTER" -> {
                if (context.combatAttacker() != null) return List.of(context.combatAttacker());
                if (room.getPendingReaction() != null && room.getPendingReaction().getAttackerInstanceId() != null) {
                    return room.findCreatureOnAnyBoard(room.getPendingReaction().getAttackerInstanceId())
                            .map(List::of).orElse(List.of());
                }
                return List.of();
            }
            case "TARGETED_ALLY_MONSTER" -> {
                if (room.getPendingReaction() != null) {
                    String tid = room.getPendingReaction().getDefenderTargetId() != null
                            ? room.getPendingReaction().getDefenderTargetId()
                            : room.getPendingReaction().getPendingTargetId();
                    if (tid != null) {
                        return room.findCreatureOnAnyBoard(tid).map(List::of).orElse(List.of());
                    }
                }
                return List.of();
            }
            case "ALL_ALLY_MONSTERS" -> {
                return actor.getAllMonsters();
            }
            case "ALL_ALLY_CREATURES" -> {
                return actor.getAllBoardCreatures();
            }
            case "ALL_OPPONENT_MONSTERS" -> {
                return room.getOpponentPlayers(actor.getTeamId()).stream()
                        .flatMap(p -> p.getAllMonsters().stream())
                        .toList();
            }
            case "ALL_OPPONENT_FAMILIARS" -> {
                return room.getOpponentPlayers(actor.getTeamId()).stream()
                        .flatMap(p -> p.getAllFamiliars().stream())
                        .toList();
            }
            case "ALL_OPPONENT_CREATURES" -> {
                return room.getOpponentPlayers(actor.getTeamId()).stream()
                        .flatMap(p -> p.getAllBoardCreatures().stream())
                        .toList();
            }
            case "UP_TO_2_OPPONENT_CREATURES" -> {
                List<CardInstance> candidates = findTargetCandidates(room, actor, source, targetSpec);
                List<CardInstance> chosen = new ArrayList<>();
                pickMatching(candidates, context.targetId()).ifPresent(chosen::add);
                pickMatching(candidates, context.secondaryTargetId())
                        .filter(c -> chosen.stream().noneMatch(x -> x.getInstanceId().equals(c.getInstanceId())))
                        .ifPresent(chosen::add);
                if (chosen.isEmpty() && context.targetId() == null && context.secondaryTargetId() == null) {
                    for (int i = 0; i < Math.min(2, candidates.size()); i++) {
                        chosen.add(candidates.get(i));
                    }
                }
                if (strictTargeting && context.targetId() != null && !context.targetId().isBlank() && chosen.isEmpty()) {
                    throw new IllegalArgumentException("Cible invalide pour cet effet.");
                }
                return chosen;
            }
            case "ALLY_MONSTER" -> {
                if (room.getPendingReaction() != null && room.getPendingReaction().getDefenderTargetId() != null) {
                    Optional<CardInstance> defended = room.findCreatureOnAnyBoard(room.getPendingReaction().getDefenderTargetId());
                    if (defended.isPresent()) {
                        return List.of(defended.get());
                    }
                }
                return selectSingleTarget(room, actor, source, targetSpec, strictTargeting, context.targetId());
            }
            default -> {
                return selectSingleTarget(room, actor, source, targetSpec, strictTargeting, context.targetId());
            }
        }
    }

    private List<CardInstance> selectSingleTarget(
            GameRoom room,
            PlayerState actor,
            CardInstance source,
            String targetSpec,
            boolean strictTargeting,
            String requestedTargetId) {
        List<CardInstance> candidates = findTargetCandidates(room, actor, source, targetSpec);
        if (requestedTargetId != null && !requestedTargetId.isBlank()) {
            Optional<CardInstance> match = pickMatching(candidates, requestedTargetId);
            if (match.isPresent()) {
                return List.of(match.get());
            }
            throw new IllegalArgumentException("Cible invalide, camouflée ou non éligible : " + requestedTargetId);
        }
        if (candidates.isEmpty()) {
            if (strictTargeting) {
                throw new IllegalArgumentException("Aucune cible valide disponible pour cet effet.");
            }
            return List.of();
        }
        return List.of(candidates.getFirst());
    }

    private Optional<CardInstance> pickMatching(List<CardInstance> candidates, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        return candidates.stream().filter(c -> c.getInstanceId().equals(instanceId)).findFirst();
    }

    public List<CardInstance> findTargetCandidates(GameRoom room, PlayerState actor, CardInstance source, String targetSpec) {
        List<PlayerState> opponents = room.getOpponentPlayers(actor.getTeamId());
        List<PlayerState> allies = room.getTeamPlayers(actor.getTeamId());

        return switch (targetSpec) {
            case "ALLY_MONSTER" -> allies.stream()
                    .flatMap(p -> p.getAllMonsters().stream())
                    .toList();
            case "ALLY_OTHER_MONSTER" -> actor.getAllMonsters().stream()
                    .filter(m -> source == null || !m.getInstanceId().equals(source.getInstanceId()))
                    .toList();
            case "OPPONENT_MONSTER" -> opponents.stream()
                    .flatMap(p -> p.getAllMonsters().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .toList();
            case "OPPONENT_FAMILIAR" -> opponents.stream()
                    .flatMap(p -> p.getAllFamiliars().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .toList();
            case "OPPONENT_CREATURE", "UP_TO_2_OPPONENT_CREATURES" -> opponents.stream()
                    .flatMap(p -> p.getAllBoardCreatures().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .toList();
            case "OPPONENT_CREATURE_COST_LE_2" -> opponents.stream()
                    .flatMap(p -> p.getAllBoardCreatures().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .filter(c -> c.getDefinition().energyCost() <= 2)
                    .toList();
            case "OPPONENT_MONSTER_DEF_LE_3" -> opponents.stream()
                    .flatMap(p -> p.getAllMonsters().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .filter(c -> evaluator.evaluateEffectiveDef(room, c) <= 3)
                    .toList();
            case "OPPONENT_MONSTER_ATK_LE_2" -> opponents.stream()
                    .flatMap(p -> p.getAllMonsters().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .filter(c -> evaluator.evaluateEffectiveAtk(room, c) <= 2)
                    .toList();
            case "OPPONENT_FAMILIAR_DEF_1" -> opponents.stream()
                    .flatMap(p -> p.getAllFamiliars().stream())
                    .filter(c -> !evaluator.hasKeyword(room, c, "CAMOUFLAGE"))
                    .filter(c -> evaluator.evaluateEffectiveDef(room, c) == 1)
                    .toList();
            default -> List.of();
        };
    }

    private ModifierDuration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return ModifierDuration.END_OF_TURN;
        }
        return switch (durationStr) {
            case "END_OF_COMBAT" -> ModifierDuration.END_OF_COMBAT;
            case "END_OF_NEXT_OPPONENT_TURN", "NEXT_OPPONENT_TURN" -> ModifierDuration.END_OF_NEXT_OPPONENT_TURN;
            case "UNTIL_NEXT_OWN_TURN" -> ModifierDuration.UNTIL_NEXT_OWN_TURN;
            case "PERMANENT" -> ModifierDuration.PERMANENT;
            default -> ModifierDuration.END_OF_TURN;
        };
    }

    @FunctionalInterface
    public interface DrawCallback {
        void drawOneOrDeckOut(GameRoom room, PlayerState player);
    }

    public record AbilityContext(
            String targetId,
            String secondaryTargetId,
            Integer choiceIndex,
            boolean deferChoiceIfMissing,
            GamePhase returnPhase,
            CardInstance combatAttacker,
            CardInstance combatTarget,
            DrawCallback drawCallback
    ) {
        public static AbilityContext empty() {
            return new AbilityContext(null, null, null, false, GamePhase.MAIN_PHASE, null, null, null);
        }
    }

    public record AbilityOutcome(boolean awaitingChoice) {
        public static AbilityOutcome completed() {
            return new AbilityOutcome(false);
        }

        public static AbilityOutcome deferredChoice() {
            return new AbilityOutcome(true);
        }
    }
}
