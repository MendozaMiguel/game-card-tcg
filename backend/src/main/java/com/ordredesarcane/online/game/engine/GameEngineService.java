package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.catalog.AbilityDefinition;
import com.ordredesarcane.online.catalog.AbilityOption;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.dto.GameErrorEvent;
import com.ordredesarcane.online.game.dto.PlayerGameView;
import com.ordredesarcane.online.game.engine.AbilityInterpreter.AbilityContext;
import com.ordredesarcane.online.game.engine.AbilityInterpreter.AbilityOutcome;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.CardInstance;
import com.ordredesarcane.online.game.model.EndReason;
import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GamePhase;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PendingModalChoice;
import com.ordredesarcane.online.game.model.PendingReactionWindow;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.game.model.Posture;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.game.model.TeamState;
import com.ordredesarcane.online.match.MatchPersistenceService;
import com.ordredesarcane.online.match.MatchSource;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class GameEngineService {

    private static final Logger log = LoggerFactory.getLogger(GameEngineService.class);

    public static final int OPENING_HAND_SIZE = 5;
    public static final long MULLIGAN_SECONDS = 20L;
    public static final long TURN_SECONDS_1V1 = 60L;
    public static final long TURN_SECONDS_2V2 = 75L;
    public static final long REACTION_SECONDS = 12L;
    public static final long DISCARD_SECONDS = 15L;
    public static final long DISCONNECT_GRACE_1V1_SECONDS = 60L;
    public static final long DISCONNECT_RELAY_2V2_SECONDS = 30L;

    private final BoardRuleEvaluator evaluator;
    private final AbilityInterpreter abilities;
    private final CombatResolver combatResolver;
    private final FogOfWarProjector projector;
    private final MageAutomateBot botAi;
    private final MatchPersistenceService matchPersistenceService;
    private final SimpMessagingTemplate messagingTemplate;

    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger cardInstanceCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<UUID, GameRoom> activeRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> userToActiveMatch = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> phaseTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    private volatile boolean asyncBotEnabled = true;

    @Autowired
    public GameEngineService(
            BoardRuleEvaluator evaluator,
            AbilityInterpreter abilities,
            CombatResolver combatResolver,
            FogOfWarProjector projector,
            MageAutomateBot botAi,
            MatchPersistenceService matchPersistenceService,
            SimpMessagingTemplate messagingTemplate) {
        this.evaluator = evaluator;
        this.abilities = abilities;
        this.combatResolver = combatResolver;
        this.projector = projector;
        this.botAi = botAi;
        this.matchPersistenceService = matchPersistenceService;
        this.messagingTemplate = messagingTemplate;
    }

    public void setAsyncBotEnabled(boolean enabled) {
        this.asyncBotEnabled = enabled;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    public record MatchParticipantSpec(
            UUID userId,
            String username,
            boolean bot,
            TeamId teamId,
            List<CardDefinition> deckCards,
            String cardBackSkin,
            String boardSkin,
            String pet,
            String energySkin,
            String avatar,
            String leagueFrame,
            String title,
            String finisher,
            AiDifficulty aiDifficulty
    ) {
        public MatchParticipantSpec {
            aiDifficulty = bot ? AiDifficulty.resolveOrDefault(aiDifficulty) : null;
        }

        public MatchParticipantSpec(
                UUID userId,
                String username,
                boolean bot,
                TeamId teamId,
                List<CardDefinition> deckCards,
                String cardBackSkin,
                String boardSkin,
                String pet,
                String energySkin,
                String avatar,
                String leagueFrame,
                String title,
                String finisher) {
            this(
                    userId, username, bot, teamId, deckCards,
                    cardBackSkin, boardSkin, pet, energySkin, avatar, leagueFrame, title, finisher,
                    bot ? AiDifficulty.MEDIUM : null);
        }

        public static MatchParticipantSpec simple(
                UUID userId,
                String username,
                boolean bot,
                TeamId teamId,
                List<CardDefinition> deckCards) {
            return simple(userId, username, bot, teamId, deckCards, AiDifficulty.MEDIUM);
        }

        public static MatchParticipantSpec simple(
                UUID userId,
                String username,
                boolean bot,
                TeamId teamId,
                List<CardDefinition> deckCards,
                AiDifficulty aiDifficulty) {
            return new MatchParticipantSpec(
                    userId, username, bot, teamId, deckCards,
                    "card_back_default", "board_sanctuary", "pet_none", "energy_arcane",
                    "avatar_initiate", "frame_apprenti", "Apprenti Arcane", "finisher_arcane_burst",
                    bot ? AiDifficulty.resolveOrDefault(aiDifficulty) : null);
        }
    }

    public GameRoom createMatch(
            GameMode mode,
            boolean ranked,
            boolean aiMatch,
            boolean customRoom,
            boolean shuffleDecks,
            List<MatchParticipantSpec> specs) {
        return createMatch(
                mode,
                ranked,
                aiMatch,
                customRoom,
                MatchSource.resolve(ranked, aiMatch, customRoom, null),
                null,
                null,
                shuffleDecks,
                specs);
    }

    public GameRoom createMatch(
            GameMode mode,
            boolean ranked,
            boolean aiMatch,
            boolean customRoom,
            MatchSource matchSource,
            UUID tournamentId,
            String tournamentName,
            boolean shuffleDecks,
            List<MatchParticipantSpec> specs) {
        UUID matchId = UUID.randomUUID();
        List<MatchParticipantSpec> teamASpecs = specs.stream().filter(s -> s.teamId() == TeamId.TEAM_A).toList();
        List<MatchParticipantSpec> teamBSpecs = specs.stream().filter(s -> s.teamId() == TeamId.TEAM_B).toList();

        if (mode == GameMode.DUEL_1V1 && (teamASpecs.size() != 1 || teamBSpecs.size() != 1)) {
            throw new IllegalArgumentException("Un duel 1v1 exige exactement 1 joueur par équipe.");
        }
        if (mode == GameMode.TEAM_2V2 && (teamASpecs.size() != 2 || teamBSpecs.size() != 2)) {
            throw new IllegalArgumentException("Un match 2v2 exige exactement 2 joueurs par équipe.");
        }

        List<MatchParticipantSpec> orderedSpecs = new ArrayList<>();
        if (mode == GameMode.DUEL_1V1) {
            orderedSpecs.add(teamASpecs.getFirst());
            orderedSpecs.add(teamBSpecs.getFirst());
        } else {
            orderedSpecs.add(teamASpecs.get(0));
            orderedSpecs.add(teamBSpecs.get(0));
            orderedSpecs.add(teamASpecs.get(1));
            orderedSpecs.add(teamBSpecs.get(1));
        }

        int startingLp = mode == GameMode.DUEL_1V1 ? 25 : 40;
        TeamState teamA = new TeamState(TeamId.TEAM_A, startingLp, teamASpecs.stream().map(MatchParticipantSpec::userId).toList());
        TeamState teamB = new TeamState(TeamId.TEAM_B, startingLp, teamBSpecs.stream().map(MatchParticipantSpec::userId).toList());

        List<PlayerState> playerStates = new ArrayList<>();
        for (int i = 0; i < orderedSpecs.size(); i++) {
            MatchParticipantSpec spec = orderedSpecs.get(i);
            PlayerState ps = new PlayerState(
                    spec.userId(),
                    spec.username(),
                    spec.bot(),
                    spec.aiDifficulty(),
                    spec.teamId(),
                    i,
                    spec.cardBackSkin(),
                    spec.boardSkin(),
                    spec.pet(),
                    spec.energySkin(),
                    spec.avatar(),
                    spec.leagueFrame(),
                    spec.title(),
                    spec.finisher());
            List<CardInstance> instances = new ArrayList<>();
            java.util.Map<com.ordredesarcane.online.catalog.Faction, Integer> factionCounts =
                    new java.util.EnumMap<>(com.ordredesarcane.online.catalog.Faction.class);
            for (CardDefinition def : spec.deckCards()) {
                factionCounts.merge(def.faction(), 1, Integer::sum);
                instances.add(new CardInstance("C" + cardInstanceCounter.incrementAndGet(), spec.userId(), def, false));
            }
            com.ordredesarcane.online.catalog.Faction deckPrimary = factionCounts.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse(com.ordredesarcane.online.catalog.Faction.FIRE);
            ps.setPrimaryFaction(deckPrimary);
            if (shuffleDecks) {
                Collections.shuffle(instances, random);
            }
            ps.getDeck().addAll(instances);
            for (int d = 0; d < OPENING_HAND_SIZE && !ps.getDeck().isEmpty(); d++) {
                ps.getHand().add(ps.getDeck().removeFirst());
            }
            playerStates.add(ps);
            if (!ps.isBot()) {
                userToActiveMatch.put(ps.getUserId(), matchId);
            }
        }

        GameRoom room = new GameRoom(
                matchId,
                mode,
                ranked,
                aiMatch,
                customRoom,
                matchSource,
                tournamentId,
                tournamentName,
                teamA,
                teamB,
                playerStates);
        room.addLog(null, "Système", "MATCH_START",
                "Ouverture du duel " + (mode == GameMode.DUEL_1V1 ? "1v1 (25 PV)" : "2v2 (40 PV communs)")
                        + ". Trêve du Tour 1 active.");
        activeRooms.put(matchId, room);

        room.getLock().lock();
        try {
            if (shuffleDecks) {
                for (PlayerState ps : playerStates) {
                    if (ps.isBot()) {
                        performMulliganSwapInternal(room, ps, botAi.chooseMulliganAction(room, ps).instanceIds());
                    }
                }
            }
            boolean allDone = room.getPlayers().values().stream().allMatch(PlayerState::isMulliganCompleted);
            if (allDone) {
                beginTurnInternal(room);
            } else {
                room.setPhase(GamePhase.MULLIGAN);
                schedulePhaseDeadlineLocked(room, MULLIGAN_SECONDS, () -> expireMulliganLocked(room));
            }
        } finally {
            room.getLock().unlock();
        }

        broadcastRoomState(room);
        triggerBotIfNeeded(room);
        return room;
    }

    public GameRoom getRoomOrThrow(UUID matchId) {
        GameRoom room = activeRooms.get(matchId);
        if (room == null) {
            throw new ResourceNotFoundException("Salle de jeu introuvable ou expirée : " + matchId);
        }
        return room;
    }

    public Optional<UUID> findActiveMatchForUser(UUID userId) {
        UUID matchId = userToActiveMatch.get(userId);
        if (matchId == null) {
            return Optional.empty();
        }
        GameRoom room = activeRooms.get(matchId);
        if (room == null || room.isFinished()) {
            userToActiveMatch.remove(userId, matchId);
            return Optional.empty();
        }
        return Optional.of(matchId);
    }

    public PlayerGameView getGameViewForUser(UUID matchId, UUID userId) {
        GameRoom room = getRoomOrThrow(matchId);
        room.getLock().lock();
        try {
            boolean isParticipant = room.getPlayers().containsKey(userId);
            boolean isSpectator = !isParticipant && room.getSpectatorIds().contains(userId);
            if (!isParticipant && !isSpectator && !room.isCustomRoom()) {
                throw new AccessDeniedException("Vous ne participez pas à cette partie.");
            }
            if (!isParticipant && !isSpectator) {
                room.getSpectatorIds().add(userId);
                isSpectator = true;
            }
            return projector.projectForPlayer(room, userId, isSpectator);
        } finally {
            room.getLock().unlock();
        }
    }

    public void addSpectator(UUID matchId, UUID userId) {
        GameRoom room = getRoomOrThrow(matchId);
        room.getLock().lock();
        try {
            if (!room.getPlayers().containsKey(userId)) {
                room.getSpectatorIds().add(userId);
            }
        } finally {
            room.getLock().unlock();
        }
        broadcastRoomState(room);
    }

    public PlayerGameView processAction(UUID senderUserId, GameActionCommand command) {
        GameRoom room = getRoomOrThrow(command.matchId());
        room.getLock().lock();
        try {
            if (room.isFinished()) {
                throw new IllegalArgumentException("La partie est déjà terminée.");
            }

            PlayerState actor = resolveAuthorizedActorLocked(room, senderUserId, command);
            switch (command.actionType()) {
                case MULLIGAN_REPLACE_CARD -> handleMulliganReplaceCardLocked(room, actor, command);
                case MULLIGAN_SWAP -> handleMulliganLocked(room, actor, command);
                case PLAY_CARD -> handlePlayCardLocked(room, actor, command);
                case SET_REACTIVE_SPELL -> handleSetReactiveSpellLocked(room, actor, command);
                case CHANGE_POSTURE -> handleChangePostureLocked(room, actor, command);
                case ACTIVATE_EFFECT -> handleActivateEffectLocked(room, actor, command);
                case ENTER_COMBAT -> handleEnterCombatLocked(room, actor);
                case DECLARE_ATTACK -> handleDeclareAttackLocked(room, actor, command);
                case TRIGGER_REACTION -> handleTriggerReactionLocked(room, actor, command);
                case PASS_REACTION -> handlePassReactionLocked(room, actor);
                case RESOLVE_CHOICE -> handleResolveChoiceLocked(room, actor, command);
                case DISCARD_EXCESS -> handleDiscardExcessLocked(room, actor, command);
                case END_TURN -> handleEndTurnLocked(room, actor);
                case FORFEIT -> finishMatchLocked(room, actor.getTeamId().opponent(), EndReason.FORFEIT,
                        actor.getUsername() + " a déclaré forfait.");
            }

            postActionChecksLocked(room);
            PlayerGameView callerView = projector.projectForPlayer(room, senderUserId, false);
            return callerView;
        } catch (RuntimeException ex) {
            sendErrorToUser(senderUserId, command.matchId(), "ACTION_REJECTED", ex.getMessage());
            throw ex;
        } finally {
            room.getLock().unlock();
            broadcastRoomState(room);
            triggerBotIfNeeded(room);
        }
    }

    public void runBotLoopSynchronouslyForTest(UUID matchId) {
        GameRoom room = getRoomOrThrow(matchId);
        int guard = 0;
        while (guard++ < 40) {
            GameActionCommand cmd = null;
            UUID botId = null;
            room.getLock().lock();
            try {
                if (room.isFinished()) {
                    return;
                }
                if (room.getPhase() == GamePhase.WAITING_FOR_REACTION && room.getPendingReaction() != null) {
                    PlayerState responder = room.getPlayer(room.getPendingReaction().getCurrentResponderPlayerId());
                    if (responder != null && responder.isBot()) {
                        botId = responder.getUserId();
                        cmd = botAi.chooseReactionAction(room, responder);
                    }
                } else if (room.getPhase() == GamePhase.WAITING_FOR_MODAL_CHOICE && room.getPendingChoice() != null) {
                    PlayerState owner = room.getPlayer(room.getPendingChoice().ownerPlayerId());
                    if (owner != null && owner.isBot()) {
                        botId = owner.getUserId();
                        cmd = botAi.chooseModalAction(room, owner);
                    }
                } else if (room.getPhase() == GamePhase.WAITING_FOR_DISCARD) {
                    PlayerState active = room.getActivePlayer();
                    if (active != null && active.isBot()) {
                        botId = active.getUserId();
                        cmd = botAi.chooseDiscardAction(room, active);
                    }
                } else if (room.getPhase() == GamePhase.MULLIGAN) {
                    for (PlayerState ps : room.getPlayers().values()) {
                        if (ps.isBot() && !ps.isMulliganCompleted()) {
                            botId = ps.getUserId();
                            cmd = botAi.chooseMulliganAction(room, ps);
                            break;
                        }
                    }
                } else if (room.getPhase() == GamePhase.MAIN_PHASE || room.getPhase() == GamePhase.COMBAT_PHASE) {
                    PlayerState active = room.getActivePlayer();
                    if (active != null && active.isBot()) {
                        botId = active.getUserId();
                        cmd = botAi.chooseNextTurnAction(room, active);
                    }
                }
            } finally {
                room.getLock().unlock();
            }
            if (cmd == null || botId == null) {
                return;
            }
            processAction(botId, cmd);
        }
    }

    public void onPlayerDisconnected(UUID userId) {
        UUID matchId = userToActiveMatch.get(userId);
        if (matchId == null) {
            return;
        }
        GameRoom room = activeRooms.get(matchId);
        if (room == null || room.isFinished()) {
            return;
        }
        room.getLock().lock();
        try {
            PlayerState ps = room.getPlayer(userId);
            if (ps == null || ps.isBot() || !ps.isConnected()) {
                return;
            }
            ps.setConnected(false);
            room.addLog(userId, ps.getUsername(), "DISCONNECTED",
                    ps.getUsername() + " s'est déconnecté.");
            scheduleDisconnectHandlingLocked(room, ps);
        } finally {
            room.getLock().unlock();
        }
        broadcastRoomState(room);
    }

    public void onPlayerReconnected(UUID userId) {
        UUID matchId = userToActiveMatch.get(userId);
        if (matchId == null) {
            return;
        }
        GameRoom room = activeRooms.get(matchId);
        if (room == null) {
            return;
        }
        cancelTimer(disconnectTimers, userId);
        room.getLock().lock();
        try {
            PlayerState ps = room.getPlayer(userId);
            if (ps == null) {
                return;
            }
            ps.setConnected(true);
            ps.setControlledByUserId(null);
            room.addLog(userId, ps.getUsername(), "RECONNECTED",
                    ps.getUsername() + " s'est reconnecté et reprend le contrôle de son plateau.");
        } finally {
            room.getLock().unlock();
        }
        broadcastRoomState(room);
    }

    private PlayerState resolveAuthorizedActorLocked(GameRoom room, UUID senderUserId, GameActionCommand command) {
        UUID targetActorId = command.actingAsPlayerId() != null ? command.actingAsPlayerId() : senderUserId;
        PlayerState targetActor = room.getPlayer(targetActorId);
        if (targetActor == null) {
            throw new AccessDeniedException("Joueur cible introuvable dans cette partie.");
        }
        if (!targetActor.canBeControlledBy(senderUserId)) {
            throw new AccessDeniedException("Action non autorisée pour ce joueur.");
        }
        return targetActor;
    }

    private void handleMulliganReplaceCardLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        if (room.getPhase() != GamePhase.MULLIGAN) {
            throw new IllegalArgumentException("La phase de Mulligan est terminée.");
        }
        if (actor.isMulliganCompleted()) {
            throw new IllegalArgumentException("Vous avez déjà validé votre Mulligan.");
        }
        String cardId = requireNonBlank(command.cardInstanceId(), "cardInstanceId obligatoire pour remplacer une carte.");
        List<CardInstance> hand = actor.getHand();
        int slotIdx = -1;
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).getInstanceId().equals(cardId)) {
                slotIdx = i;
                break;
            }
        }
        if (slotIdx < 0) {
            throw new IllegalArgumentException("Carte de Mulligan introuvable en main : " + cardId);
        }
        if (actor.hasMulliganReplacedSlot(slotIdx)) {
            throw new IllegalArgumentException("Cet emplacement a déjà été remplacé pendant ce Mulligan.");
        }
        if (!actor.getDeck().isEmpty()) {
            CardInstance oldCard = hand.get(slotIdx);
            Collections.shuffle(actor.getDeck(), random);
            CardInstance drawn = actor.getDeck().removeFirst();
            hand.set(slotIdx, drawn);
            actor.getDeck().add(oldCard);
            Collections.shuffle(actor.getDeck(), random);
        }
        actor.markMulliganSlotReplaced(slotIdx);
        room.addLog(actor.getUserId(), actor.getUsername(), "MULLIGAN",
                actor.getUsername() + " remplace une carte de départ.");
    }

    private void handleMulliganLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        if (room.getPhase() != GamePhase.MULLIGAN) {
            throw new IllegalArgumentException("La phase de Mulligan est terminée.");
        }
        if (actor.isMulliganCompleted()) {
            throw new IllegalArgumentException("Vous avez déjà effectué votre Mulligan.");
        }
        performMulliganSwapInternal(room, actor, command.instanceIds());
        if (room.getPlayers().values().stream().allMatch(PlayerState::isMulliganCompleted)) {
            beginTurnInternal(room);
        }
    }

    private void performMulliganSwapInternal(GameRoom room, PlayerState actor, List<String> replaceIds) {
        List<CardInstance> returned = new ArrayList<>();
        if (replaceIds != null && !replaceIds.isEmpty()) {
            Set<String> uniqueIds = new HashSet<>(replaceIds);
            for (String id : uniqueIds) {
                int slotIdx = -1;
                for (int i = 0; i < actor.getHand().size(); i++) {
                    if (actor.getHand().get(i).getInstanceId().equals(id)) {
                        slotIdx = i;
                        break;
                    }
                }
                if (slotIdx < 0) {
                    throw new IllegalArgumentException("Carte de Mulligan introuvable en main : " + id);
                }
                if (actor.hasMulliganReplacedSlot(slotIdx)) {
                    throw new IllegalArgumentException("Cet emplacement a déjà été remplacé pendant ce Mulligan.");
                }
                actor.markMulliganSlotReplaced(slotIdx);
                returned.add(actor.getHand().get(slotIdx));
            }
            actor.getHand().removeAll(returned);
            actor.getDeck().addAll(returned);
            Collections.shuffle(actor.getDeck(), random);
            for (int i = 0; i < returned.size() && !actor.getDeck().isEmpty(); i++) {
                actor.getHand().add(actor.getDeck().removeFirst());
            }
        }
        actor.setMulliganCompleted(true);
        room.addLog(actor.getUserId(), actor.getUsername(), "MULLIGAN",
                actor.getUsername() + " valide sa main de départ (" + actor.getMulliganReplacedSlots().size() + " remplacée(s)).");
    }

    private void beginTurnInternal(GameRoom room) {
        if (room.isFinished()) {
            return;
        }
        PlayerState active = room.getActivePlayer();
        room.setCombatPhaseStartedThisTurn(false);
        active.setHealedThisTurn(false);
        active.setDirectAttacksBlockedThisTurn(false);
        active.setSpellsLockedThisTurn(active.isSpellsLockedNextTurn());
        active.setSpellsLockedNextTurn(false);
        active.getPeekedOpponentTopCards().clear();

        for (CardInstance c : active.getAllBoardCreatures()) {
            c.onOwnerTurnStart();
        }
        for (CardInstance s : active.getAllSpellArtifacts()) {
            s.setActivatedThisTurn(false);
        }

        active.addEnergy(1);
        room.addLog(active.getUserId(), active.getUsername(), "TURN_START",
                "Tour " + room.getTurnNumber() + " (Manche " + room.getRoundNumber() + ") — "
                        + active.getUsername() + " (+1 Énergie -> " + active.getEnergyTokens() + ").");

        for (CardInstance c : new ArrayList<>(active.getAllBoardCreatures())) {
            abilities.executeAbilitiesForTrigger(room, active, c, "ON_TURN_START", false, AbilityContext.empty());
        }

        for (PlayerState opponent : room.getOpponentPlayers(active.getTeamId())) {
            List<CardInstance> oppSources = new ArrayList<>(opponent.getAllBoardCreatures());
            for (CardInstance spell : opponent.getAllSpellArtifacts()) {
                if (!spell.isFaceDown()) {
                    oppSources.add(spell);
                }
            }
            for (CardInstance src : oppSources) {
                abilities.executeAbilitiesForTrigger(room, opponent, src, "ON_OPPONENT_TURN_START", false, AbilityContext.empty());
            }
        }

        drawOneOrDeckOut(room, active);
        if (room.isFinished()) {
            return;
        }

        evaluator.cleanupZeroDefCreatures(room);
        checkLpVictoryLocked(room);
        if (room.isFinished()) {
            return;
        }

        room.setPhase(GamePhase.MAIN_PHASE);
        long turnSeconds = room.getMode() == GameMode.DUEL_1V1 ? TURN_SECONDS_1V1 : TURN_SECONDS_2V2;
        room.setSavedTurnRemainingMillis(turnSeconds * 1_000L);
        schedulePhaseDeadlineLocked(room, turnSeconds, () -> expireTurnLocked(room, active.getUserId()));
    }

    private void handlePlayCardLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        ensureActiveTurnInMainPhase(room, actor);
        String instanceId = requireNonBlank(command.cardInstanceId(), "cardInstanceId obligatoire.");
        CardInstance card = actor.findInHand(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Carte absente de votre main."));
        CardDefinition def = card.getDefinition();

        if (def.cardType() == CardType.SPELL_REACTIVE) {
            throw new IllegalArgumentException("Les Sorts Réactifs se posent face cachée avec SET_REACTIVE_SPELL.");
        }
        if (def.cardType().isSpellCategory() && actor.isSpellsLockedThisTurn()) {
            throw new IllegalArgumentException("Vos Sorts sont verrouillés pour ce tour (Nuit Éternelle).");
        }
        if (actor.getEnergyTokens() < def.energyCost()) {
            throw new IllegalArgumentException("Énergie insuffisante (" + actor.getEnergyTokens() + "/" + def.energyCost() + ").");
        }

        switch (def.cardType()) {
            case MONSTER -> {
                if (def.isEpic() && actor.hasEpicMonsterOnBoard()) {
                    throw new IllegalArgumentException("Restriction : 1 seul Monstre Épique autorisé simultanément sur votre terrain.");
                }
                int slot = chooseSlotOrThrow(actor.getMonsters(), command.targetSlot(), PlayerState.MAX_MONSTER_SLOTS, "Ligne de Front pleine (4/4).");
                actor.spendEnergy(def.energyCost());
                actor.getHand().remove(card);
                card.setSlot(slot);
                card.setPosture(command.posture() != null ? command.posture() : Posture.ATTACK);
                actor.getMonsters()[slot] = card;
                room.addLog(actor.getUserId(), actor.getUsername(), "PLAY_MONSTER",
                        actor.getUsername() + " invoque " + def.name() + " (" + card.getPosture() + ").");
                abilities.executeAbilitiesForTrigger(room, actor, card, "ON_PLAY", false,
                        new AbilityContext(command.targetId(), command.secondaryTargetId(), command.choiceIndex(),
                                false, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
            }
            case FAMILIAR -> {
                int slot = chooseSlotOrThrow(actor.getFamiliars(), command.targetSlot(), PlayerState.MAX_FAMILIAR_SLOTS, "Ligne de Familiers pleine (3/3).");
                actor.spendEnergy(def.energyCost());
                actor.getHand().remove(card);
                card.setSlot(slot);
                card.setPosture(command.posture() != null ? command.posture() : Posture.ATTACK);
                actor.getFamiliars()[slot] = card;
                room.addLog(actor.getUserId(), actor.getUsername(), "PLAY_FAMILIAR",
                        actor.getUsername() + " invoque le Familier " + def.name() + " (" + card.getPosture() + ").");
                abilities.executeAbilitiesForTrigger(room, actor, card, "ON_PLAY", false,
                        new AbilityContext(command.targetId(), command.secondaryTargetId(), command.choiceIndex(),
                                false, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
            }
            case CURSE, ARTIFACT -> {
                int slot = chooseSlotOrThrow(actor.getSpellArtifacts(), command.targetSlot(), PlayerState.MAX_SPELL_ARTIFACT_SLOTS,
                        "Emplacements Sorts/Artefacts pleins (2/2).");
                actor.spendEnergy(def.energyCost());
                actor.getHand().remove(card);
                card.setSlot(slot);
                card.setFaceDown(false);
                if (def.abilitiesScript().stream().anyMatch(a ->
                        "PERSISTENT_2_TURNS".equals(a.duration()) || "PERSISTENT_2_TURNS".equals(a.condition()))) {
                    card.setCurseRemainingOpponentTurns(2);
                }
                actor.getSpellArtifacts()[slot] = card;
                room.addLog(actor.getUserId(), actor.getUsername(), "PLAY_PERSISTENT_SPELL",
                        actor.getUsername() + " active " + def.name() + " sur sa Ligne de Soutien.");
                fireOnSpellCastTriggers(room, actor);
            }
            case SPELL_NORMAL, SPELL_ULTIMATE -> {
                if (!abilities.hasValidTargetForSpellPlay(room, actor, card, command.choiceIndex())) {
                    throw new IllegalArgumentException("Aucune cible valide pour jouer " + def.name() + ".");
                }
                if (command.targetId() != null && !command.targetId().isBlank()) {
                    CardInstance targeted = room.findCreatureOnAnyBoard(command.targetId()).orElse(null);
                    if (targeted != null && !targeted.getOwnerPlayerId().equals(actor.getUserId())
                            && targeted.getDefinition().cardType() == CardType.MONSTER
                            && hasEligibleReactionForSpellTarget(room, actor, targeted)) {
                        actor.spendEnergy(def.energyCost());
                        actor.getHand().remove(card);
                        actor.getDiscard().add(card);
                        room.addLog(actor.getUserId(), actor.getUsername(), "CAST_SPELL_PENDING",
                                actor.getUsername() + " lance " + def.name() + " !");
                        fireOnSpellCastTriggers(room, actor);
                        openSpellTargetReactionWindowLocked(room, actor, card, targeted, command);
                        return;
                    }
                }
                AbilityOutcome outcome = abilities.executeAbilitiesForTrigger(room, actor, card, "ON_PLAY", true,
                        new AbilityContext(command.targetId(), command.secondaryTargetId(), command.choiceIndex(),
                                command.choiceIndex() == null, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
                actor.spendEnergy(def.energyCost());
                actor.getHand().remove(card);
                actor.getDiscard().add(card);
                if (!outcome.awaitingChoice()) {
                    room.addLog(actor.getUserId(), actor.getUsername(), "CAST_SPELL",
                            actor.getUsername() + " lance " + def.name() + ".");
                }
                fireOnSpellCastTriggers(room, actor);
            }
            default -> throw new IllegalArgumentException("Type de carte non supporté.");
        }
    }

    private void handleSetReactiveSpellLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        ensureActiveTurnInMainPhase(room, actor);
        String instanceId = requireNonBlank(command.cardInstanceId(), "cardInstanceId obligatoire.");
        CardInstance card = actor.findInHand(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Carte absente de votre main."));
        if (card.getDefinition().cardType() != CardType.SPELL_REACTIVE) {
            throw new IllegalArgumentException("Seul un Sort Réactif peut être posé face cachée.");
        }
        if (actor.isSpellsLockedThisTurn()) {
            throw new IllegalArgumentException("Vos Sorts sont verrouillés pour ce tour.");
        }
        if (actor.getEnergyTokens() < card.getDefinition().energyCost()) {
            throw new IllegalArgumentException("Énergie insuffisante pour poser ce Sort Réactif.");
        }
        int slot = chooseSlotOrThrow(actor.getSpellArtifacts(), command.targetSlot(), PlayerState.MAX_SPELL_ARTIFACT_SLOTS,
                "Emplacements Sorts/Artefacts pleins (2/2).");

        actor.spendEnergy(card.getDefinition().energyCost());
        actor.getHand().remove(card);
        card.setSlot(slot);
        card.setFaceDown(true);
        actor.getSpellArtifacts()[slot] = card;
        room.addLog(actor.getUserId(), actor.getUsername(), "SET_REACTIVE",
                actor.getUsername() + " pose un Sort Réactif face cachée.");
    }

    private void handleChangePostureLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        ensureActiveTurnInMainPhase(room, actor);
        String instanceId = requireNonBlank(command.cardInstanceId(), "cardInstanceId obligatoire.");
        CardInstance card = actor.findOnBoard(instanceId)
                .filter(c -> !c.getDefinition().cardType().isSpellCategory())
                .orElseThrow(() -> new IllegalArgumentException("Créature introuvable sur votre terrain."));
        if (card.isChangedPostureThisTurn()) {
            throw new IllegalArgumentException("Cette créature a déjà changé de posture ce tour-ci.");
        }
        card.setPosture(card.getPosture() == Posture.ATTACK ? Posture.DEFENSE : Posture.ATTACK);
        card.setChangedPostureThisTurn(true);
        room.addLog(actor.getUserId(), actor.getUsername(), "CHANGE_POSTURE",
                card.getDefinition().name() + " passe en position " + card.getPosture() + ".");
    }

    private void handleActivateEffectLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        ensureActiveTurnInMainPhase(room, actor);
        String instanceId = requireNonBlank(command.cardInstanceId(), "cardInstanceId obligatoire.");
        CardInstance card = actor.findOnBoard(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Carte introuvable sur votre terrain."));
        if (card.isFaceDown()) {
            throw new IllegalArgumentException("Une carte face cachée ne peut pas s'activer manuellement.");
        }
        if (card.isActivatedThisTurn()) {
            throw new IllegalArgumentException("Effet déjà activé ce tour-ci (max 1 fois/tour/carte).");
        }
        if (card.isExhausted()) {
            throw new IllegalArgumentException("Ce Familier est épuisé jusqu'à votre prochain tour.");
        }
        boolean hasActivated = card.getDefinition().abilitiesScript().stream()
                .anyMatch(a -> "ACTIVATED".equals(a.trigger()));
        if (!hasActivated) {
            throw new IllegalArgumentException("Cette carte n'a pas d'effet activable manuellement.");
        }

        if ("SHADOW_F_10".equals(card.getDefinition().cardId())
                && abilities.findTargetCandidates(room, actor, card, "OPPONENT_FAMILIAR").isEmpty()) {
            throw new IllegalArgumentException("Aucun Familier adverse valide à cibler pour Sacrifice Vorace.");
        }

        if (card.getDefinition().cardType() == CardType.FAMILIAR && hasEligibleReactionForFamiliarEffect(room, actor)) {
            card.setActivatedThisTurn(true);
            openFamiliarEffectReactionWindowLocked(room, actor, card, command);
            return;
        }

        AbilityOutcome outcome = abilities.executeAbilitiesForTrigger(room, actor, card, "ACTIVATED", true,
                new AbilityContext(command.targetId(), command.secondaryTargetId(), command.choiceIndex(),
                        command.choiceIndex() == null, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
        card.setActivatedThisTurn(true);
        if (!outcome.awaitingChoice()) {
            room.addLog(actor.getUserId(), actor.getUsername(), "ACTIVATE_EFFECT",
                    actor.getUsername() + " active l'effet de " + card.getDefinition().name() + " (0 Énergie).");
        }
    }

    private void handleEnterCombatLocked(GameRoom room, PlayerState actor) {
        ensureActiveTurnInMainPhase(room, actor);
        if (room.isTurn1TruceActive()) {
            throw new IllegalArgumentException("Trêve du Tour 1 : aucune Phase de Combat autorisée au 1er tour.");
        }
        enterCombatPhaseIfNeededLocked(room, actor);
    }

    private void enterCombatPhaseIfNeededLocked(GameRoom room, PlayerState actor) {
        if (room.isCombatPhaseStartedThisTurn()) {
            return;
        }
        room.setCombatPhaseStartedThisTurn(true);
        room.setPhase(GamePhase.COMBAT_PHASE);
        room.addLog(actor.getUserId(), actor.getUsername(), "COMBAT_PHASE_START",
                actor.getUsername() + " entre en Phase de Combat.");
        for (CardInstance c : new ArrayList<>(actor.getAllBoardCreatures())) {
            abilities.executeAbilitiesForTrigger(room, actor, c, "ON_COMBAT_PHASE_START", false, AbilityContext.empty());
        }
    }

    private void handleDeclareAttackLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        if (!room.getActivePlayer().getUserId().equals(actor.getUserId())) {
            throw new IllegalArgumentException("Ce n'est pas votre tour.");
        }
        if (room.getPhase() != GamePhase.MAIN_PHASE && room.getPhase() != GamePhase.COMBAT_PHASE) {
            throw new IllegalArgumentException("Impossible d'attaquer pendant la phase : " + room.getPhase());
        }
        if (room.isTurn1TruceActive()) {
            throw new IllegalArgumentException("Trêve du Tour 1 : aucune Phase de Combat lors du 1er tour de table.");
        }

        String attackerId = requireNonBlank(command.cardInstanceId(), "cardInstanceId attaquant obligatoire.");
        String targetId = requireNonBlank(command.targetId(), "targetId obligatoire.");
        CardInstance attacker = actor.findOnBoard(attackerId)
                .filter(c -> !c.getDefinition().cardType().isSpellCategory())
                .orElseThrow(() -> new IllegalArgumentException("Attaquant introuvable sur votre terrain."));

        combatResolver.validateAttackLegality(room, actor, attacker, targetId);

        enterCombatPhaseIfNeededLocked(room, actor);
        if (room.isFinished()) {
            return;
        }
        boolean directAttack = CombatResolver.DIRECT_PLAYER_TARGET.equals(targetId);

        CardInstance combatTarget = directAttack ? null : room.findCreatureOnAnyBoard(targetId).orElse(null);
        AbilityOutcome outcome = abilities.executeAbilitiesForTrigger(room, actor, attacker, "ON_ATTACK", false,
                new AbilityContext(targetId, null, command.choiceIndex(), command.choiceIndex() == null,
                        GamePhase.COMBAT_PHASE, attacker, combatTarget, this::drawOneOrDeckOut));
        if (outcome.awaitingChoice()) {
            return;
        }

        beginAttackReactionOrResolveLocked(room, actor, attacker, targetId, directAttack);
    }

    private void beginAttackReactionOrResolveLocked(
            GameRoom room,
            PlayerState attackerPlayer,
            CardInstance attacker,
            String targetId,
            boolean directAttack) {
        if (!evaluator.hasKeyword(room, attacker, "CHARGE")) {
            PendingReactionWindow draft = new PendingReactionWindow(
                    PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED,
                    GamePhase.COMBAT_PHASE,
                    computeRemainingTurnMillis(room),
                    Instant.now().plusSeconds(REACTION_SECONDS),
                    attackerPlayer.getUserId(),
                    attacker.getInstanceId(),
                    directAttack ? null : targetId,
                    directAttack,
                    attackerPlayer.getUserId(),
                    attacker.getInstanceId(),
                    targetId,
                    null,
                    null,
                    -1,
                    List.of());

            List<UUID> responders = new ArrayList<>();
            for (PlayerState opp : room.getOpponentPlayers(attackerPlayer.getTeamId())) {
                if (!projector.findEligibleReactiveCardsForResponder(room, opp, draft).isEmpty()) {
                    responders.add(opp.getUserId());
                }
            }
            if (!responders.isEmpty()) {
                room.setSavedTurnRemainingMillis(draft.getSavedTurnRemainingMillis());
                PendingReactionWindow window = new PendingReactionWindow(
                        PendingReactionWindow.ReactionTriggerType.ATTACK_DECLARED,
                        GamePhase.COMBAT_PHASE,
                        draft.getSavedTurnRemainingMillis(),
                        Instant.now().plusSeconds(REACTION_SECONDS),
                        attackerPlayer.getUserId(),
                        attacker.getInstanceId(),
                        directAttack ? null : targetId,
                        directAttack,
                        attackerPlayer.getUserId(),
                        attacker.getInstanceId(),
                        targetId,
                        null,
                        null,
                        -1,
                        responders);
                room.setPendingReaction(window);
                room.setPhase(GamePhase.WAITING_FOR_REACTION);
                room.addLog(attackerPlayer.getUserId(), attackerPlayer.getUsername(), "REACTION_WINDOW",
                        "Fenêtre de Réaction (12s) ouverte pour contrer l'attaque de " + attacker.getDefinition().name() + ".");
                schedulePhaseDeadlineLocked(room, REACTION_SECONDS, () -> expireReactionWindowLocked(room, window.getWindowId()));
                return;
            }
        }

        combatResolver.resolveCombat(room, attackerPlayer, attacker, targetId, directAttack);
    }

    private void handleTriggerReactionLocked(GameRoom room, PlayerState responder, GameActionCommand command) {
        if (room.getPhase() != GamePhase.WAITING_FOR_REACTION || room.getPendingReaction() == null) {
            throw new IllegalArgumentException("Aucune Fenêtre de Réaction active.");
        }
        PendingReactionWindow rw = room.getPendingReaction();
        if (!responder.getUserId().equals(rw.getCurrentResponderPlayerId())) {
            throw new IllegalArgumentException("Ce n'est pas à vous de répondre.");
        }
        if (command.cardInstanceId() == null || command.cardInstanceId().isBlank()) {
            handlePassReactionLocked(room, responder);
            return;
        }

        List<String> eligible = projector.findEligibleReactiveCardsForResponder(room, responder, rw);
        if (!eligible.contains(command.cardInstanceId())) {
            throw new IllegalArgumentException("Carte réactive non éligible pour cet événement.");
        }
        CardInstance reactiveCard = responder.findOnBoard(command.cardInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("Carte réactive introuvable sur votre terrain."));

        Integer choiceIdx = command.choiceIndex();
        if (choiceIdx == null && "LIGHT_S_04".equals(reactiveCard.getDefinition().cardId())) {
            choiceIdx = rw.isDirectAttack() ? 1 : 0;
        }
        abilities.executeAbilitiesForTrigger(room, responder, reactiveCard, "REACTIVE", true,
                new AbilityContext(command.targetId(), command.secondaryTargetId(), choiceIdx,
                        false, GamePhase.WAITING_FOR_REACTION, null, null, this::drawOneOrDeckOut));

        if (reactiveCard.getDefinition().cardType() == CardType.SPELL_REACTIVE) {
            reactiveCard.setFaceDown(false);
            responder.removeFromBoard(reactiveCard.getInstanceId());
            fireOnSpellCastTriggers(room, responder);
        } else {
            reactiveCard.setExhausted(true);
        }

        room.addLog(responder.getUserId(), responder.getUsername(), "REACTION_TRIGGERED",
                responder.getUsername() + " active en réaction : " + reactiveCard.getDefinition().name() + " !");

        finalizeReactionWindowLocked(room, rw);
    }

    private void handlePassReactionLocked(GameRoom room, PlayerState responder) {
        if (room.getPhase() != GamePhase.WAITING_FOR_REACTION || room.getPendingReaction() == null) {
            throw new IllegalArgumentException("Aucune Fenêtre de Réaction active.");
        }
        PendingReactionWindow rw = room.getPendingReaction();
        if (!responder.getUserId().equals(rw.getCurrentResponderPlayerId())) {
            throw new IllegalArgumentException("Ce n'est pas à vous de passer.");
        }
        rw.advanceResponder(responder.getUserId());
        if (rw.hasRemainingResponders()) {
            schedulePhaseDeadlineLocked(room, REACTION_SECONDS, () -> expireReactionWindowLocked(room, rw.getWindowId()));
            return;
        }
        finalizeReactionWindowLocked(room, rw);
    }

    private void finalizeReactionWindowLocked(GameRoom room, PendingReactionWindow rw) {
        room.setPendingReaction(null);
        room.setPhase(rw.getReturnPhase());
        restoreSavedTurnTimerLocked(room, rw.getSavedTurnRemainingMillis());

        switch (rw.getTriggerType()) {
            case ATTACK_DECLARED -> {
                if (!rw.isActionCancelled()) {
                    PlayerState attackerPlayer = room.getPlayer(rw.getAttackerPlayerId());
                    CardInstance attacker = room.findCreatureOnAnyBoard(rw.getAttackerInstanceId()).orElse(null);
                    if (attackerPlayer != null && attacker != null) {
                        String finalTarget = rw.isDirectAttack()
                                ? CombatResolver.DIRECT_PLAYER_TARGET
                                : rw.getDefenderTargetId();
                        combatResolver.resolveCombat(room, attackerPlayer, attacker, finalTarget, rw.isDirectAttack());
                    }
                }
            }
            case FAMILIAR_EFFECT_ACTIVATED -> {
                if (!rw.isActionCancelled()) {
                    PlayerState sourcePlayer = room.getPlayer(rw.getSourceActorPlayerId());
                    CardInstance familiar = room.findCreatureOnAnyBoard(rw.getSourceCardInstanceId()).orElse(null);
                    if (sourcePlayer != null && familiar != null) {
                        abilities.executeAbilitiesForTrigger(room, sourcePlayer, familiar, "ACTIVATED", false,
                                new AbilityContext(rw.getPendingTargetId(), rw.getPendingSecondaryTargetId(),
                                        rw.getPendingChoiceIndex(), false, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
                    }
                }
            }
            case SPELL_TARGETED_ALLY_MONSTER -> {
                if (!rw.isActionCancelled()) {
                    PlayerState caster = room.getPlayer(rw.getSourceActorPlayerId());
                    CardInstance spell = caster != null
                            ? caster.getDiscard().stream()
                            .filter(c -> c.getInstanceId().equals(rw.getSourceCardInstanceId()))
                            .findFirst().orElse(null)
                            : null;
                    if (caster != null && spell != null) {
                        abilities.executeAbilitiesForTrigger(room, caster, spell, "ON_PLAY", false,
                                new AbilityContext(rw.getPendingTargetId(), rw.getPendingSecondaryTargetId(),
                                        rw.getPendingChoiceIndex(), false, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
                    }
                }
            }
        }
    }

    private void handleResolveChoiceLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        if (room.getPhase() != GamePhase.WAITING_FOR_MODAL_CHOICE || room.getPendingChoice() == null) {
            throw new IllegalArgumentException("Aucun choix d'effet en attente.");
        }
        PendingModalChoice pc = room.getPendingChoice();
        if (!actor.getUserId().equals(pc.ownerPlayerId())) {
            throw new IllegalArgumentException("Ce choix ne vous appartient pas.");
        }
        if (command.choiceIndex() == null || command.choiceIndex() < 0 || command.choiceIndex() >= pc.options().size()) {
            throw new IllegalArgumentException("Index de choix invalide.");
        }
        room.setPendingChoice(null);
        room.setPhase(pc.returnPhase());

        CardInstance source = room.findAnyCardOnAnyBoard(pc.sourceInstanceId())
                .orElseGet(() -> actor.getDiscard().stream()
                        .filter(c -> c.getInstanceId().equals(pc.sourceInstanceId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Source du choix introuvable.")));

        AbilityOption opt = pc.options().get(command.choiceIndex());
        String target = command.targetId() != null ? command.targetId() : pc.preselectedTargetId();
        room.addLog(actor.getUserId(), actor.getUsername(), "MODAL_CHOICE",
                actor.getUsername() + " choisit [" + opt.label() + "] pour " + source.getDefinition().name() + ".");

        if (pc.returnPhase() == GamePhase.COMBAT_PHASE) {
            AbilityContext ctx = new AbilityContext(target, command.secondaryTargetId(), command.choiceIndex(),
                    false, GamePhase.COMBAT_PHASE, source, null, this::drawOneOrDeckOut);
            abilities.executeAbilitiesForTrigger(room, actor, source, "ON_ATTACK", false, ctx);
            boolean direct = CombatResolver.DIRECT_PLAYER_TARGET.equals(pc.preselectedTargetId());
            beginAttackReactionOrResolveLocked(room, actor, source, pc.preselectedTargetId(), direct);
        } else {
            String trigger = source.getDefinition().cardType().isSpellCategory() ? "ON_PLAY" : "ACTIVATED";
            abilities.executeAbilitiesForTrigger(room, actor, source, trigger, true,
                    new AbilityContext(target, command.secondaryTargetId(), command.choiceIndex(),
                            false, GamePhase.MAIN_PHASE, null, null, this::drawOneOrDeckOut));
        }
    }

    private void handleEndTurnLocked(GameRoom room, PlayerState actor) {
        if (!room.getActivePlayer().getUserId().equals(actor.getUserId())) {
            throw new IllegalArgumentException("Ce n'est pas votre tour.");
        }
        if (room.getPhase() != GamePhase.MAIN_PHASE && room.getPhase() != GamePhase.COMBAT_PHASE) {
            throw new IllegalArgumentException("Impossible de terminer le tour pendant la phase : " + room.getPhase());
        }

        for (CardInstance c : new ArrayList<>(actor.getAllBoardCreatures())) {
            abilities.executeAbilitiesForTrigger(room, actor, c, "ON_TURN_END", false, AbilityContext.empty());
        }

        for (PlayerState opponent : room.getOpponentPlayers(actor.getTeamId())) {
            List<CardInstance> oppSources = new ArrayList<>(opponent.getAllBoardCreatures());
            for (CardInstance spell : opponent.getAllSpellArtifacts()) {
                if (!spell.isFaceDown()) oppSources.add(spell);
            }
            for (CardInstance src : oppSources) {
                abilities.executeAbilitiesForTrigger(room, opponent, src, "ON_OPPONENT_TURN_END", false, AbilityContext.empty());
            }

            for (CardInstance spell : opponent.getAllSpellArtifacts()) {
                if (spell.getCurseRemainingOpponentTurns() != null) {
                    int remaining = spell.getCurseRemainingOpponentTurns() - 1;
                    if (remaining <= 0) {
                        opponent.removeFromBoard(spell.getInstanceId());
                        room.addLog(opponent.getUserId(), opponent.getUsername(), "CURSE_EXPIRED",
                                "La malédiction " + spell.getDefinition().name() + " se dissipe.");
                    } else {
                        spell.setCurseRemainingOpponentTurns(remaining);
                    }
                }
            }
        }

        for (PlayerState ps : room.getPlayers().values()) {
            for (CardInstance c : ps.getAllBoardCreatures()) {
                c.expireEndOfTurnModifiers(actor.getTeamId());
                if (ps.getUserId().equals(actor.getUserId())) {
                    c.onOwnerTurnEnd();
                }
            }
        }

        evaluator.cleanupZeroDefCreatures(room);
        checkLpVictoryLocked(room);
        if (room.isFinished()) {
            return;
        }

        if (actor.getHand().size() > PlayerState.MAX_HAND_SIZE_END_TURN) {
            room.setPhase(GamePhase.WAITING_FOR_DISCARD);
            int toDiscard = actor.getHand().size() - PlayerState.MAX_HAND_SIZE_END_TURN;
            room.addLog(actor.getUserId(), actor.getUsername(), "DISCARD_REQUIRED",
                    actor.getUsername() + " a " + actor.getHand().size() + " cartes en main (max 6) : doit défausser " + toDiscard + " carte(s).");
            schedulePhaseDeadlineLocked(room, DISCARD_SECONDS, () -> expireDiscardLocked(room, actor.getUserId()));
            return;
        }

        advanceTurnToNextPlayerLocked(room);
    }

    private void handleDiscardExcessLocked(GameRoom room, PlayerState actor, GameActionCommand command) {
        if (room.getPhase() != GamePhase.WAITING_FOR_DISCARD || !room.getActivePlayer().getUserId().equals(actor.getUserId())) {
            throw new IllegalArgumentException("Aucune défausse excédentaire attendue.");
        }
        List<String> ids = command.instanceIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez les cartes à défausser.");
        }
        int required = actor.getHand().size() - PlayerState.MAX_HAND_SIZE_END_TURN;
        if (ids.size() != required) {
            throw new IllegalArgumentException("Vous devez défausser exactement " + required + " carte(s) pour revenir à 6.");
        }
        for (String id : new HashSet<>(ids)) {
            CardInstance found = actor.findInHand(id)
                    .orElseThrow(() -> new IllegalArgumentException("Carte introuvable en main : " + id));
            actor.getHand().remove(found);
            actor.getDiscard().add(found);
        }
        if (actor.getHand().size() > PlayerState.MAX_HAND_SIZE_END_TURN) {
            throw new IllegalArgumentException("Il reste encore trop de cartes en main.");
        }
        room.addLog(actor.getUserId(), actor.getUsername(), "DISCARD_EXCESS",
                actor.getUsername() + " défausse son surplus et revient à 6 cartes.");
        advanceTurnToNextPlayerLocked(room);
    }

    private void advanceTurnToNextPlayerLocked(GameRoom room) {
        int nextIdx = (room.getActiveTurnIndex() + 1) % room.getTurnOrder().size();
        if (nextIdx == 0) {
            room.incrementRoundNumber();
            if (room.getRoundNumber() == 2) {
                room.addLog(null, "Système", "TRUCE_ENDED",
                        "Fin de la Trêve du Tour 1 ! La Phase de Combat est désormais débloquée.");
            }
        }
        room.setActiveTurnIndex(nextIdx);
        room.incrementTurnNumber();
        beginTurnInternal(room);
    }

    private void drawOneOrDeckOut(GameRoom room, PlayerState player) {
        if (room.isFinished()) {
            return;
        }
        if (player.getDeck().isEmpty()) {
            finishMatchLocked(room, player.getTeamId().opponent(), EndReason.DECK_OUT,
                    player.getUsername() + " n'a plus de carte à piocher (Deck-out) !");
            return;
        }
        CardInstance drawn = player.getDeck().removeFirst();
        player.getHand().add(drawn);
        room.addLog(player.getUserId(), player.getUsername(), "DRAW",
                player.getUsername() + " pioche 1 carte.");
    }

    private void postActionChecksLocked(GameRoom room) {
        if (!room.isFinished()) {
            evaluator.cleanupZeroDefCreatures(room);
            checkLpVictoryLocked(room);
        }
    }

    private void checkLpVictoryLocked(GameRoom room) {
        if (room.isFinished()) {
            return;
        }
        int lpA = room.getTeam(TeamId.TEAM_A).getLp();
        int lpB = room.getTeam(TeamId.TEAM_B).getLp();
        if (lpA <= 0 && lpB <= 0) {
            TeamId activeTeam = room.getActivePlayer().getTeamId();
            finishMatchLocked(room, activeTeam, EndReason.LP_ZERO, "Les deux équipes atteignent 0 PV !");
        } else if (lpA <= 0) {
            finishMatchLocked(room, TeamId.TEAM_B, EndReason.LP_ZERO, "L'Équipe A tombe à 0 PV !");
        } else if (lpB <= 0) {
            finishMatchLocked(room, TeamId.TEAM_A, EndReason.LP_ZERO, "L'Équipe B tombe à 0 PV !");
        }
    }

    private void finishMatchLocked(GameRoom room, TeamId winnerTeam, EndReason reason, String logMessage) {
        if (room.isFinished()) {
            return;
        }
        room.setWinnerTeam(winnerTeam);
        room.setEndReason(reason);
        room.setFinishedAt(Instant.now());
        room.setPhase(GamePhase.FINISHED);
        room.setPhaseDeadline(null);
        room.setPendingReaction(null);
        room.setPendingChoice(null);
        room.addLog(null, "Système", "MATCH_FINISHED",
                logMessage + " Victoire de " + winnerTeam + " !");

        cancelTimer(phaseTimers, room.getMatchId());
        for (PlayerState ps : room.getPlayers().values()) {
            cancelTimer(disconnectTimers, ps.getUserId());
            if (!ps.isBot()) {
                userToActiveMatch.remove(ps.getUserId(), room.getMatchId());
            }
        }
        try {
            matchPersistenceService.persistCompletedMatch(room);
        } catch (Exception ex) {
            log.warn("Persistance de fin de match ignorée/échouée pour {} : {}", room.getMatchId(), ex.getMessage());
        }
    }

    private void fireOnSpellCastTriggers(GameRoom room, PlayerState caster) {
        for (CardInstance monster : new ArrayList<>(caster.getAllMonsters())) {
            abilities.executeAbilitiesForTrigger(room, caster, monster, "ON_SPELL_CAST", false, AbilityContext.empty());
        }
    }

    private boolean hasEligibleReactionForFamiliarEffect(GameRoom room, PlayerState caster) {
        for (PlayerState opp : room.getOpponentPlayers(caster.getTeamId())) {
            if (opp.isSpellsLockedThisTurn()) continue;
            for (CardInstance s : opp.getAllSpellArtifacts()) {
                if (s.isFaceDown() && s.getDefinition().abilitiesScript().stream()
                        .anyMatch(a -> "REACTIVE".equals(a.trigger()) && "ON_FAMILIAR_EFFECT".equals(a.condition()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasEligibleReactionForSpellTarget(GameRoom room, PlayerState caster, CardInstance targetMonster) {
        for (PlayerState opp : room.getOpponentPlayers(caster.getTeamId())) {
            if (opp.isSpellsLockedThisTurn()) continue;
            for (CardInstance s : opp.getAllSpellArtifacts()) {
                if (s.isFaceDown() && s.getDefinition().abilitiesScript().stream()
                        .anyMatch(a -> "REACTIVE".equals(a.trigger()) && "ON_ALLY_TARGETED".equals(a.condition()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void openFamiliarEffectReactionWindowLocked(GameRoom room, PlayerState caster, CardInstance familiar, GameActionCommand cmd) {
        List<UUID> responders = new ArrayList<>();
        for (PlayerState opp : room.getOpponentPlayers(caster.getTeamId())) {
            if (!opp.isSpellsLockedThisTurn() && opp.getAllSpellArtifacts().stream().anyMatch(s ->
                    s.isFaceDown() && s.getDefinition().abilitiesScript().stream()
                            .anyMatch(a -> "REACTIVE".equals(a.trigger()) && "ON_FAMILIAR_EFFECT".equals(a.condition())))) {
                responders.add(opp.getUserId());
            }
        }
        long remaining = computeRemainingTurnMillis(room);
        room.setSavedTurnRemainingMillis(remaining);
        PendingReactionWindow rw = new PendingReactionWindow(
                PendingReactionWindow.ReactionTriggerType.FAMILIAR_EFFECT_ACTIVATED,
                GamePhase.MAIN_PHASE,
                remaining,
                Instant.now().plusSeconds(REACTION_SECONDS),
                null, null, null, false,
                caster.getUserId(),
                familiar.getInstanceId(),
                cmd.targetId(),
                cmd.secondaryTargetId(),
                cmd.choiceIndex(),
                -1,
                responders);
        room.setPendingReaction(rw);
        room.setPhase(GamePhase.WAITING_FOR_REACTION);
        schedulePhaseDeadlineLocked(room, REACTION_SECONDS, () -> expireReactionWindowLocked(room, rw.getWindowId()));
    }

    private void openSpellTargetReactionWindowLocked(
            GameRoom room,
            PlayerState caster,
            CardInstance spellCard,
            CardInstance targetedMonster,
            GameActionCommand cmd) {
        List<UUID> responders = new ArrayList<>();
        for (PlayerState opp : room.getOpponentPlayers(caster.getTeamId())) {
            if (!opp.isSpellsLockedThisTurn() && opp.getAllSpellArtifacts().stream().anyMatch(s ->
                    s.isFaceDown() && s.getDefinition().abilitiesScript().stream()
                            .anyMatch(a -> "REACTIVE".equals(a.trigger()) && "ON_ALLY_TARGETED".equals(a.condition())))) {
                responders.add(opp.getUserId());
            }
        }
        long remaining = computeRemainingTurnMillis(room);
        room.setSavedTurnRemainingMillis(remaining);
        PendingReactionWindow rw = new PendingReactionWindow(
                PendingReactionWindow.ReactionTriggerType.SPELL_TARGETED_ALLY_MONSTER,
                GamePhase.MAIN_PHASE,
                remaining,
                Instant.now().plusSeconds(REACTION_SECONDS),
                null, null, targetedMonster.getInstanceId(), false,
                caster.getUserId(),
                spellCard.getInstanceId(),
                cmd.targetId(),
                cmd.secondaryTargetId(),
                cmd.choiceIndex(),
                -1,
                responders);
        room.setPendingReaction(rw);
        room.setPhase(GamePhase.WAITING_FOR_REACTION);
        schedulePhaseDeadlineLocked(room, REACTION_SECONDS, () -> expireReactionWindowLocked(room, rw.getWindowId()));
    }

    private void expireMulliganLocked(GameRoom room) {
        if (room.getPhase() != GamePhase.MULLIGAN || room.isFinished()) {
            return;
        }
        for (PlayerState ps : room.getPlayers().values()) {
            ps.setMulliganCompleted(true);
        }
        room.addLog(null, "Système", "MULLIGAN_TIMEOUT", "Chrono Mulligan expiré : début automatique du Tour 1.");
        beginTurnInternal(room);
    }

    private void expireTurnLocked(GameRoom room, UUID expectedTurnPlayerId) {
        if (room.isFinished()) {
            return;
        }
        if ((room.getPhase() == GamePhase.MAIN_PHASE || room.getPhase() == GamePhase.COMBAT_PHASE)
                && room.getActivePlayer().getUserId().equals(expectedTurnPlayerId)) {
            room.addLog(expectedTurnPlayerId, room.getActivePlayer().getUsername(), "TURN_TIMEOUT",
                    "La Corde est brûlée : fin de tour automatique.");
            handleEndTurnLocked(room, room.getActivePlayer());
        }
    }

    private void expireReactionWindowLocked(GameRoom room, UUID windowId) {
        if (room.isFinished() || room.getPhase() != GamePhase.WAITING_FOR_REACTION || room.getPendingReaction() == null) {
            return;
        }
        if (!room.getPendingReaction().getWindowId().equals(windowId)) {
            return;
        }
        room.addLog(null, "Système", "REACTION_TIMEOUT", "Temps de réaction écoulé : passage automatique.");
        finalizeReactionWindowLocked(room, room.getPendingReaction());
        postActionChecksLocked(room);
    }

    private void expireDiscardLocked(GameRoom room, UUID playerId) {
        if (room.isFinished() || room.getPhase() != GamePhase.WAITING_FOR_DISCARD) {
            return;
        }
        PlayerState active = room.getActivePlayer();
        if (!active.getUserId().equals(playerId)) {
            return;
        }
        while (active.getHand().size() > PlayerState.MAX_HAND_SIZE_END_TURN) {
            CardInstance dropped = active.getHand().removeLast();
            active.getDiscard().add(dropped);
        }
        room.addLog(active.getUserId(), active.getUsername(), "DISCARD_TIMEOUT",
                "Temps de défausse expiré : surplus défaussé automatiquement.");
        advanceTurnToNextPlayerLocked(room);
    }

    private void schedulePhaseDeadlineLocked(GameRoom room, long delaySeconds, Runnable lockedAction) {
        cancelTimer(phaseTimers, room.getMatchId());
        Instant deadline = Instant.now().plusSeconds(delaySeconds);
        room.setPhaseDeadline(deadline);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            room.getLock().lock();
            try {
                if (!room.isFinished()) {
                    lockedAction.run();
                }
            } catch (Exception ex) {
                log.error("Erreur timer match {}: {}", room.getMatchId(), ex.getMessage());
            } finally {
                room.getLock().unlock();
            }
            broadcastRoomState(room);
            triggerBotIfNeeded(room);
        }, delaySeconds, TimeUnit.SECONDS);
        phaseTimers.put(room.getMatchId(), future);
    }

    private void restoreSavedTurnTimerLocked(GameRoom room, long remainingMillis) {
        cancelTimer(phaseTimers, room.getMatchId());
        long safeMillis = Math.max(1_000L, remainingMillis);
        room.setPhaseDeadline(Instant.now().plusMillis(safeMillis));
        UUID activeId = room.getActivePlayer().getUserId();
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            room.getLock().lock();
            try {
                expireTurnLocked(room, activeId);
            } finally {
                room.getLock().unlock();
            }
            broadcastRoomState(room);
            triggerBotIfNeeded(room);
        }, safeMillis, TimeUnit.MILLISECONDS);
        phaseTimers.put(room.getMatchId(), future);
    }

    private void scheduleDisconnectHandlingLocked(GameRoom room, PlayerState disconnected) {
        cancelTimer(disconnectTimers, disconnected.getUserId());
        long delaySeconds = room.getMode() == GameMode.TEAM_2V2 ? DISCONNECT_RELAY_2V2_SECONDS : DISCONNECT_GRACE_1V1_SECONDS;
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            room.getLock().lock();
            try {
                if (room.isFinished() || disconnected.isConnected()) {
                    return;
                }
                if (room.getMode() == GameMode.TEAM_2V2) {
                    PlayerState partner = room.findTeammate(disconnected.getUserId()).orElse(null);
                    if (partner != null && partner.isConnected() && !partner.isBot()) {
                        disconnected.setControlledByUserId(partner.getUserId());
                        room.addLog(partner.getUserId(), partner.getUsername(), "ARCANUM_RELAY",
                                "Relais d'Arcane activé : " + partner.getUsername()
                                        + " prend les commandes de la main et du terrain de " + disconnected.getUsername() + " !");
                    } else {
                        finishMatchLocked(room, disconnected.getTeamId().opponent(), EndReason.DISCONNECT_TIMEOUT,
                                "Toute l'équipe " + disconnected.getTeamId() + " est déconnectée.");
                    }
                } else {
                    finishMatchLocked(room, disconnected.getTeamId().opponent(), EndReason.DISCONNECT_TIMEOUT,
                            disconnected.getUsername() + " ne s'est pas reconnecté à temps (60s).");
                }
            } finally {
                room.getLock().unlock();
            }
            broadcastRoomState(room);
        }, delaySeconds, TimeUnit.SECONDS);
        disconnectTimers.put(disconnected.getUserId(), future);
    }

    private void triggerBotIfNeeded(GameRoom room) {
        if (!asyncBotEnabled || room.isFinished()) {
            return;
        }
        boolean botShouldAct = false;
        room.getLock().lock();
        try {
            if (room.getPhase() == GamePhase.WAITING_FOR_REACTION && room.getPendingReaction() != null) {
                PlayerState responder = room.getPlayer(room.getPendingReaction().getCurrentResponderPlayerId());
                botShouldAct = responder != null && responder.isBot();
            } else if (room.getPhase() == GamePhase.WAITING_FOR_MODAL_CHOICE && room.getPendingChoice() != null) {
                PlayerState owner = room.getPlayer(room.getPendingChoice().ownerPlayerId());
                botShouldAct = owner != null && owner.isBot();
            } else if (room.getActivePlayer() != null && room.getActivePlayer().isBot()
                    && (room.getPhase() == GamePhase.MAIN_PHASE
                    || room.getPhase() == GamePhase.COMBAT_PHASE
                    || room.getPhase() == GamePhase.WAITING_FOR_DISCARD)) {
                botShouldAct = true;
            }
        } finally {
            room.getLock().unlock();
        }
        if (botShouldAct) {
            scheduler.schedule(() -> runBotStepAsync(room.getMatchId()), 450, TimeUnit.MILLISECONDS);
        }
    }

    private void runBotStepAsync(UUID matchId) {
        GameRoom room = activeRooms.get(matchId);
        if (room == null || room.isFinished()) {
            return;
        }
        GameActionCommand cmd = null;
        UUID botId = null;
        room.getLock().lock();
        try {
            if (room.isFinished()) {
                return;
            }
            if (room.getPhase() == GamePhase.WAITING_FOR_REACTION && room.getPendingReaction() != null) {
                PlayerState responder = room.getPlayer(room.getPendingReaction().getCurrentResponderPlayerId());
                if (responder != null && responder.isBot()) {
                    botId = responder.getUserId();
                    cmd = botAi.chooseReactionAction(room, responder);
                }
            } else if (room.getPhase() == GamePhase.WAITING_FOR_MODAL_CHOICE && room.getPendingChoice() != null) {
                PlayerState owner = room.getPlayer(room.getPendingChoice().ownerPlayerId());
                if (owner != null && owner.isBot()) {
                    botId = owner.getUserId();
                    cmd = botAi.chooseModalAction(room, owner);
                }
            } else if (room.getPhase() == GamePhase.WAITING_FOR_DISCARD) {
                PlayerState active = room.getActivePlayer();
                if (active != null && active.isBot()) {
                    botId = active.getUserId();
                    cmd = botAi.chooseDiscardAction(room, active);
                }
            } else if (room.getPhase() == GamePhase.MAIN_PHASE || room.getPhase() == GamePhase.COMBAT_PHASE) {
                PlayerState active = room.getActivePlayer();
                if (active != null && active.isBot()) {
                    botId = active.getUserId();
                    cmd = botAi.chooseNextTurnAction(room, active);
                }
            }
        } finally {
            room.getLock().unlock();
        }
        if (cmd != null && botId != null) {
            processAction(botId, cmd);
        }
    }

    private void broadcastRoomState(GameRoom room) {
        if (messagingTemplate == null) {
            return;
        }
        Map<UUID, PlayerGameView> views = new java.util.LinkedHashMap<>();
        PlayerGameView spectatorView;
        room.getLock().lock();
        try {
            for (PlayerState ps : room.getPlayers().values()) {
                if (!ps.isBot() && ps.isConnected()) {
                    views.put(ps.getUserId(), projector.projectForPlayer(room, ps.getUserId(), false));
                }
            }
            spectatorView = room.getSpectatorIds().isEmpty()
                    ? null
                    : projector.projectForPlayer(room, null, true);
        } finally {
            room.getLock().unlock();
        }
        views.forEach((userId, view) ->
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/game-state", view));
        if (spectatorView != null) {
            messagingTemplate.convertAndSend("/topic/game." + room.getMatchId() + ".spectate", spectatorView);
        }
    }

    private void sendErrorToUser(UUID userId, UUID matchId, String code, String message) {
        if (messagingTemplate == null || userId == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                new GameErrorEvent(matchId, code, message, Instant.now()));
    }

    private void ensureActiveTurnInMainPhase(GameRoom room, PlayerState actor) {
        if (!room.getActivePlayer().getUserId().equals(actor.getUserId())) {
            throw new IllegalArgumentException("Ce n'est pas votre tour.");
        }
        if (room.getPhase() != GamePhase.MAIN_PHASE) {
            throw new IllegalArgumentException("Action impossible pendant la phase : " + room.getPhase());
        }
    }

    private int chooseSlotOrThrow(CardInstance[] slots, Integer requested, int maxSlots, String fullMsg) {
        if (requested != null) {
            if (requested < 0 || requested >= maxSlots) {
                throw new IllegalArgumentException("Emplacement invalide : " + requested);
            }
            if (slots[requested] != null) {
                throw new IllegalArgumentException("Emplacement déjà occupé : " + requested);
            }
            return requested;
        }
        for (int i = 0; i < maxSlots; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        throw new IllegalArgumentException(fullMsg);
    }

    private long computeRemainingTurnMillis(GameRoom room) {
        if (room.getPhaseDeadline() == null) {
            return room.getSavedTurnRemainingMillis();
        }
        return Math.max(1_000L, Duration.between(Instant.now(), room.getPhaseDeadline()).toMillis());
    }

    private void cancelTimer(Map<UUID, ScheduledFuture<?>> map, UUID key) {
        ScheduledFuture<?> existing = map.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
