package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.game.dto.CardViewDto;
import com.ordredesarcane.online.game.dto.PendingChoiceViewDto;
import com.ordredesarcane.online.game.dto.PendingReactionViewDto;
import com.ordredesarcane.online.game.dto.PlayerBoardViewDto;
import com.ordredesarcane.online.game.dto.PlayerGameView;
import com.ordredesarcane.online.game.dto.TeamViewDto;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PendingModalChoice;
import com.ordredesarcane.online.game.model.PendingReactionWindow;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.game.model.TeamState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FogOfWarProjector {

    private final BoardRuleEvaluator evaluator;
    private final CardCatalogService cardCatalogService;

    public FogOfWarProjector(BoardRuleEvaluator evaluator, CardCatalogService cardCatalogService) {
        this.evaluator = evaluator;
        this.cardCatalogService = cardCatalogService;
    }

    public PlayerGameView projectForPlayer(GameRoom room, UUID viewerUserId, boolean spectator) {
        List<UUID> controlledRelays = spectator ? List.of() : room.getPlayers().values().stream()
                .filter(p -> viewerUserId != null && viewerUserId.equals(p.getControlledByUserId()))
                .map(PlayerState::getUserId)
                .toList();

        List<PlayerBoardViewDto> playerViews = room.getTurnOrder().stream()
                .map(room::getPlayer)
                .map(p -> projectPlayerBoard(room, p, viewerUserId, spectator))
                .toList();

        TeamViewDto teamA = toTeamView(room.getTeam(TeamId.TEAM_A));
        TeamViewDto teamB = toTeamView(room.getTeam(TeamId.TEAM_B));

        PendingReactionViewDto reactionView = null;
        PendingReactionWindow rw = room.getPendingReaction();
        if (rw != null) {
            List<String> eligibleIds = List.of();
            if (!spectator && viewerUserId != null) {
                PlayerState responder = room.getPlayer(rw.getCurrentResponderPlayerId());
                if (responder != null && responder.canBeControlledBy(viewerUserId)) {
                    eligibleIds = findEligibleReactiveCardsForResponder(room, responder, rw);
                }
            }
            reactionView = new PendingReactionViewDto(
                    rw.getWindowId(),
                    rw.getTriggerType().name(),
                    rw.getCurrentResponderPlayerId(),
                    rw.getExpiresAt(),
                    rw.getAttackerPlayerId(),
                    rw.getAttackerInstanceId(),
                    rw.getDefenderTargetId(),
                    rw.isDirectAttack(),
                    rw.getSourceActorPlayerId(),
                    rw.getSourceCardInstanceId(),
                    eligibleIds);
        }

        PendingChoiceViewDto choiceView = null;
        PendingModalChoice pc = room.getPendingChoice();
        if (pc != null && !spectator && viewerUserId != null) {
            PlayerState choiceOwner = room.getPlayer(pc.ownerPlayerId());
            if (choiceOwner != null && choiceOwner.canBeControlledBy(viewerUserId)) {
                choiceView = new PendingChoiceViewDto(
                        pc.ownerPlayerId(),
                        pc.sourceInstanceId(),
                        pc.sourceCardName(),
                        pc.options(),
                        pc.preselectedTargetId());
            }
        }

        UUID activeId = room.getActivePlayer() != null ? room.getActivePlayer().getUserId() : null;
        return new PlayerGameView(
                room.getMatchId(),
                room.getMode(),
                room.isRanked(),
                room.isAiMatch(),
                room.isCustomRoom(),
                spectator,
                viewerUserId,
                activeId,
                controlledRelays,
                room.getPhase(),
                room.getPhaseDeadline(),
                room.getTurnNumber(),
                room.getRoundNumber(),
                room.isTurn1TruceActive(),
                teamA,
                teamB,
                playerViews,
                reactionView,
                choiceView,
                room.getWinnerTeam(),
                room.getEndReason(),
                room.getCombatLog(),
                room.getVisualEvents());
    }

    public List<String> findEligibleReactiveCardsForResponder(
            GameRoom room,
            PlayerState responder,
            PendingReactionWindow rw) {
        List<String> eligible = new ArrayList<>();
        if (responder == null) {
            return eligible;
        }
        CardInstance attacker = rw.getAttackerInstanceId() != null
                ? room.findCreatureOnAnyBoard(rw.getAttackerInstanceId()).orElse(null)
                : null;
        CardInstance attackedTarget = rw.getDefenderTargetId() != null
                ? room.findCreatureOnAnyBoard(rw.getDefenderTargetId()).orElse(null)
                : null;

        for (CardInstance spell : responder.getAllSpellArtifacts()) {
            if (!spell.isFaceDown() || responder.isSpellsLockedThisTurn()) {
                continue;
            }
            boolean match = spell.getDefinition().abilitiesScript().stream()
                    .filter(a -> "REACTIVE".equals(a.trigger()))
                    .anyMatch(a -> matchesReactiveCondition(room, responder, a.condition(), rw, attacker, attackedTarget));
            if (match) {
                eligible.add(spell.getInstanceId());
            }
        }

        for (CardInstance familiar : responder.getAllFamiliars()) {
            if (familiar.isExhausted()) {
                continue;
            }
            boolean match = familiar.getDefinition().abilitiesScript().stream()
                    .filter(a -> "REACTIVE".equals(a.trigger()))
                    .anyMatch(a -> matchesReactiveCondition(room, responder, a.condition(), rw, attacker, attackedTarget));
            if (match) {
                eligible.add(familiar.getInstanceId());
            }
        }
        return eligible;
    }

    private boolean matchesReactiveCondition(
            GameRoom room,
            PlayerState responder,
            String condition,
            PendingReactionWindow rw,
            CardInstance attacker,
            CardInstance attackedTarget) {
        if (condition == null) {
            return false;
        }
        return switch (condition) {
            case "ON_DIRECT_ATTACK" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && rw.isDirectAttack();
            case "ON_MONSTER_ATTACK" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && attacker != null
                    && attacker.getDefinition().cardType() == com.ordredesarcane.online.catalog.CardType.MONSTER;
            case "ON_COMBAT_OR_DIRECT_ATTACK" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && (rw.isDirectAttack()
                    || (attackedTarget != null && attackedTarget.getDefinition().cardType() == com.ordredesarcane.online.catalog.CardType.MONSTER));
            case "ON_ALLY_ATTACKED" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && !rw.isDirectAttack()
                    && attackedTarget != null;
            case "ON_ALLY_TARGETED" -> (rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && attackedTarget != null
                    && attackedTarget.getDefinition().cardType() == com.ordredesarcane.online.catalog.CardType.MONSTER)
                    || rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.SPELL_TARGETED_ALLY_MONSTER;
            case "ON_FAMILIAR_EFFECT" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.FAMILIAR_EFFECT_ACTIVATED;
            case "ON_ALLY_MONSTER_DESTROYED_COMBAT" -> rw.getTriggerType() == PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED
                    && attacker != null
                    && attackedTarget != null
                    && attackedTarget.getOwnerPlayerId().equals(responder.getUserId())
                    && attackedTarget.getDefinition().cardType() == com.ordredesarcane.online.catalog.CardType.MONSTER
                    && wouldBeDestroyed(room, attacker, attackedTarget);
            default -> false;
        };
    }

    private boolean wouldBeDestroyed(GameRoom room, CardInstance attacker, CardInstance defender) {
        int atkA = evaluator.evaluateEffectiveAtk(room, attacker);
        if (defender.getPosture() == com.ordredesarcane.online.game.model.Posture.ATTACK) {
            return atkA >= evaluator.evaluateEffectiveAtk(room, defender);
        }
        return atkA > evaluator.evaluateEffectiveDef(room, defender);
    }

    private PlayerBoardViewDto projectPlayerBoard(
            GameRoom room,
            PlayerState player,
            UUID viewerUserId,
            boolean spectator) {
        boolean seesPrivateZone = !spectator && viewerUserId != null && player.canBeControlledBy(viewerUserId);

        List<CardViewDto> handView = player.getHand().stream()
                .map(c -> seesPrivateZone ? toRevealedView(room, c, false) : toHiddenView(c, player.getCardBackSkin(), false))
                .toList();

        List<CardViewDto> monsterViews = Arrays.stream(player.getMonsters())
                .filter(Objects::nonNull)
                .map(c -> toRevealedView(room, c, false))
                .toList();

        List<CardViewDto> familiarViews = Arrays.stream(player.getFamiliars())
                .filter(Objects::nonNull)
                .map(c -> toRevealedView(room, c, false))
                .toList();

        List<CardViewDto> spellViews = Arrays.stream(player.getSpellArtifacts())
                .filter(Objects::nonNull)
                .map(c -> (!c.isFaceDown() || seesPrivateZone)
                        ? toRevealedView(room, c, c.isFaceDown())
                        : toHiddenView(c, player.getCardBackSkin(), true))
                .toList();

        List<CardViewDto> discardView = player.getDiscard().stream()
                .map(c -> toRevealedView(room, c, false))
                .toList();

        CardViewDto peekedTopCard = null;
        if (seesPrivateZone && !player.getPeekedOpponentTopCards().isEmpty()) {
            String firstPeekedCardId = player.getPeekedOpponentTopCards().values().iterator().next();
            CardDefinition def = cardCatalogService.findById(firstPeekedCardId).orElse(null);
            if (def != null) {
                peekedTopCard = new CardViewDto(
                        "PEEK",
                        null,
                        -1,
                        false,
                        false,
                        player.getCardBackSkin(),
                        false,
                        def.cardId(),
                        def.name(),
                        def.faction(),
                        def.cardType(),
                        def.isEpic(),
                        def.isRamp(),
                        def.energyCost(),
                        def.baseAtk(),
                        def.baseDef(),
                        def.baseAtk(),
                        def.baseDef(),
                        def.keywords(),
                        def.description(),
                        def.abilitiesScript(),
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null);
            }
        }

        return new PlayerBoardViewDto(
                player.getUserId(),
                player.getUsername(),
                player.isBot(),
                player.getTeamId(),
                player.getSeatIndex(),
                player.isConnected(),
                player.getControlledByUserId(),
                player.isMulliganCompleted(),
                seesPrivateZone ? player.getMulliganReplacedSlots() : List.of(),
                player.getEnergyTokens(),
                player.getDeck().size(),
                player.getHand().size(),
                handView,
                monsterViews,
                familiarViews,
                spellViews,
                discardView,
                peekedTopCard,
                player.isSpellsLockedThisTurn(),
                player.isDirectAttacksBlockedThisTurn(),
                player.getCardBackSkin(),
                player.getBoardSkin(),
                player.getPet(),
                player.getEnergySkin(),
                player.getAvatar(),
                player.getLeagueFrame(),
                player.getTitle(),
                player.getFinisher(),
                player.isBot() ? player.getAiDifficulty() : null,
                player.getPrimaryFaction());
    }

    private CardViewDto toRevealedView(GameRoom room, CardInstance card, boolean faceDownToOpponents) {
        CardDefinition def = card.getDefinition();
        boolean isCreature = !def.cardType().isSpellCategory() && card.getSlot() >= 0;
        Integer effectiveAtk = isCreature ? Integer.valueOf(evaluator.evaluateEffectiveAtk(room, card)) : def.baseAtk();
        Integer effectiveDef = isCreature ? Integer.valueOf(evaluator.evaluateEffectiveDef(room, card)) : def.baseDef();
        List<String> keywords = isCreature ? evaluator.evaluateKeywords(room, card) : def.keywords();

        return new CardViewDto(
                card.getInstanceId(),
                card.getOwnerPlayerId(),
                card.getSlot(),
                false,
                faceDownToOpponents,
                null,
                card.isFoil(),
                def.cardId(),
                def.name(),
                def.faction(),
                def.cardType(),
                def.isEpic(),
                def.isRamp(),
                def.energyCost(),
                def.baseAtk(),
                def.baseDef(),
                effectiveAtk,
                effectiveDef,
                keywords,
                def.description(),
                def.abilitiesScript(),
                card.getPosture(),
                card.isExhausted(),
                card.isSlowed(),
                card.isCamouflaged(),
                card.isActivatedThisTurn(),
                card.isChangedPostureThisTurn(),
                isCreature && evaluator.canCreatureAttackNow(room, card),
                card.getCurseRemainingOpponentTurns());
    }

    private CardViewDto toHiddenView(CardInstance card, String cardBackSkin, boolean faceDownOnBoard) {
        return new CardViewDto(
                card.getInstanceId(),
                card.getOwnerPlayerId(),
                card.getSlot(),
                true,
                faceDownOnBoard,
                cardBackSkin,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                null);
    }

    private TeamViewDto toTeamView(TeamState team) {
        return new TeamViewDto(team.getTeamId(), team.getLp(), team.getMaxLp(), team.getPlayerIds());
    }
}
