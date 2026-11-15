package com.ordredesarcane.online.lobby;

import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.lobby.dto.CreateCustomRoomRequest;
import com.ordredesarcane.online.lobby.dto.CustomRoomDto;
import com.ordredesarcane.online.lobby.dto.CustomRoomSlotDto;
import com.ordredesarcane.online.lobby.dto.JoinCustomRoomRequest;
import com.ordredesarcane.online.lobby.dto.LobbyNotificationDto;
import com.ordredesarcane.online.security.XssSanitizer;
import com.ordredesarcane.online.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class CustomRoomService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final class CustomRoomState {
        final String roomCode;
        final String name;
        final GameMode mode;
        final UUID hostUserId;
        final boolean allowSpectators;
        final Instant createdAt = Instant.now();
        final List<SlotEntry> slots = new ArrayList<>();
        final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
        boolean started;
        UUID matchId;

        CustomRoomState(String roomCode, String name, GameMode mode, UUID hostUserId, boolean allowSpectators) {
            this.roomCode = roomCode;
            this.name = name;
            this.mode = mode;
            this.hostUserId = hostUserId;
            this.allowSpectators = allowSpectators;
        }
    }

    private record SlotEntry(UUID userId, String username, UUID deckId, TeamId teamId, boolean host) {
    }

    private final ConcurrentHashMap<String, CustomRoomState> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final MatchParticipantBuilder participantBuilder;
    private final GameEngineService gameEngineService;
    private final UserRepository userRepository;
    private final XssSanitizer xssSanitizer;
    private final SimpMessagingTemplate messagingTemplate;

    public CustomRoomService(
            MatchParticipantBuilder participantBuilder,
            GameEngineService gameEngineService,
            UserRepository userRepository,
            XssSanitizer xssSanitizer,
            SimpMessagingTemplate messagingTemplate) {
        this.participantBuilder = participantBuilder;
        this.gameEngineService = gameEngineService;
        this.userRepository = userRepository;
        this.xssSanitizer = xssSanitizer;
        this.messagingTemplate = messagingTemplate;
    }

    public CustomRoomDto createRoom(UUID hostUserId, CreateCustomRoomRequest request) {
        MatchParticipantSpec hostSpec = participantBuilder.buildHumanParticipant(hostUserId, request.deckId(), TeamId.TEAM_A);
        String code = generateRoomCode();
        String safeName = xssSanitizer.sanitize(request.name(), 48);
        CustomRoomState state = new CustomRoomState(code, safeName, request.mode(), hostUserId, request.allowSpectators());
        state.slots.add(new SlotEntry(hostUserId, hostSpec.username(), request.deckId(), TeamId.TEAM_A, true));
        rooms.put(code, state);
        return toDto(state);
    }

    public List<CustomRoomDto> listJoinableRooms() {
        return rooms.values().stream()
                .filter(r -> !r.started || r.allowSpectators)
                .map(this::toDto)
                .toList();
    }

    public synchronized CustomRoomDto joinRoom(UUID userId, String roomCode, JoinCustomRoomRequest request) {
        CustomRoomState state = rooms.get(roomCode.toUpperCase());
        if (state == null) {
            throw new ResourceNotFoundException("Salle personnalisée introuvable : " + roomCode);
        }
        if (request.asSpectator()) {
            if (!state.allowSpectators) {
                throw new IllegalArgumentException("Les spectateurs sont désactivés pour cette salle.");
            }
            state.spectators.add(userId);
            if (state.started && state.matchId != null) {
                gameEngineService.addSpectator(state.matchId, userId);
            }
            return toDto(state);
        }

        if (state.started) {
            throw new IllegalArgumentException("La partie a déjà commencé.");
        }
        if (request.deckId() == null) {
            throw new IllegalArgumentException("deckId obligatoire pour rejoindre en tant que joueur.");
        }
        participantBuilder.buildHumanParticipant(userId, request.deckId(), request.preferredTeam());

        int maxPerTeam = state.mode == GameMode.DUEL_1V1 ? 1 : 2;
        long currentOnTeam = state.slots.stream()
                .filter(s -> !s.userId().equals(userId) && s.teamId() == request.preferredTeam())
                .count();
        if (currentOnTeam >= maxPerTeam) {
            throw new IllegalArgumentException("L'équipe " + request.preferredTeam() + " est complète.");
        }

        state.slots.removeIf(s -> s.userId().equals(userId));
        String username = userRepository.findById(userId).map(u -> u.getUsername()).orElse("Mage");
        state.slots.add(new SlotEntry(userId, username, request.deckId(), request.preferredTeam(), userId.equals(state.hostUserId)));
        return toDto(state);
    }

    public synchronized CustomRoomDto startRoom(UUID requesterId, String roomCode, boolean fillWithBots) {
        CustomRoomState state = rooms.get(roomCode.toUpperCase());
        if (state == null) {
            throw new ResourceNotFoundException("Salle personnalisée introuvable : " + roomCode);
        }
        if (!state.hostUserId.equals(requesterId)) {
            throw new IllegalArgumentException("Seul l'hôte peut lancer la salle.");
        }
        if (state.started) {
            return toDto(state);
        }

        int perTeam = state.mode == GameMode.DUEL_1V1 ? 1 : 2;
        List<MatchParticipantSpec> specs = new ArrayList<>();
        int teamACount = 0;
        int teamBCount = 0;
        for (SlotEntry entry : state.slots) {
            specs.add(participantBuilder.buildHumanParticipant(entry.userId(), entry.deckId(), entry.teamId()));
            if (entry.teamId() == TeamId.TEAM_A) teamACount++;
            else teamBCount++;
        }

        if (!fillWithBots && (teamACount < perTeam || teamBCount < perTeam)) {
            throw new IllegalArgumentException("Nombre de joueurs insuffisant pour lancer sans bots.");
        }
        while (teamACount < perTeam) {
            specs.add(participantBuilder.buildBotParticipant("Automate Alliée", TeamId.TEAM_A, null));
            teamACount++;
        }
        while (teamBCount < perTeam) {
            specs.add(participantBuilder.buildBotParticipant("Mage Automate", TeamId.TEAM_B, null));
            teamBCount++;
        }

        boolean hasBots = state.slots.size() < perTeam * 2;
        GameRoom gameRoom = gameEngineService.createMatch(state.mode, false, hasBots, true, true, specs);
        for (UUID specId : state.spectators) {
            gameEngineService.addSpectator(gameRoom.getMatchId(), specId);
        }
        state.started = true;
        state.matchId = gameRoom.getMatchId();

        LobbyNotificationDto event = new LobbyNotificationDto(
                "CUSTOM_ROOM_STARTED",
                gameRoom.getMatchId(),
                state.mode,
                false,
                "La salle " + state.name + " commence !",
                Instant.now());
        if (messagingTemplate != null) {
            for (SlotEntry entry : state.slots) {
                messagingTemplate.convertAndSendToUser(entry.userId().toString(), "/queue/lobby", event);
            }
            for (UUID specId : state.spectators) {
                messagingTemplate.convertAndSendToUser(specId.toString(), "/queue/lobby", event);
            }
        }
        return toDto(state);
    }

    private String generateRoomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private CustomRoomDto toDto(CustomRoomState state) {
        List<CustomRoomSlotDto> slotDtos = state.slots.stream()
                .map(s -> new CustomRoomSlotDto(s.userId(), s.username(), s.teamId(), s.host()))
                .toList();
        return new CustomRoomDto(
                state.roomCode,
                state.name,
                state.mode,
                state.hostUserId,
                state.allowSpectators,
                state.started,
                state.matchId,
                slotDtos,
                state.spectators.size(),
                state.createdAt);
    }
}
