package com.ordredesarcane.online.lobby;

import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.lobby.dto.LobbyNotificationDto;
import com.ordredesarcane.online.lobby.dto.QueueJoinRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

    private record QueueTicket(UUID userId, GameMode mode, boolean ranked, UUID deckId, Instant joinedAt) {
    }

    private final List<QueueTicket> queue = new ArrayList<>();
    private final MatchParticipantBuilder participantBuilder;
    private final GameEngineService gameEngineService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchmakingService(
            MatchParticipantBuilder participantBuilder,
            GameEngineService gameEngineService,
            SimpMessagingTemplate messagingTemplate) {
        this.participantBuilder = participantBuilder;
        this.gameEngineService = gameEngineService;
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized Map<String, Object> joinQueue(UUID userId, QueueJoinRequest request) {
        if (gameEngineService.findActiveMatchForUser(userId).isPresent()) {
            throw new IllegalArgumentException("Vous êtes déjà dans un duel actif.");
        }
        participantBuilder.buildHumanParticipant(userId, request.deckId(), TeamId.TEAM_A);

        leaveQueue(userId);
        queue.add(new QueueTicket(userId, request.mode(), request.ranked(), request.deckId(), Instant.now()));

        List<QueueTicket> matched = tryPopMatch(request.mode(), request.ranked());
        if (matched != null) {
            List<MatchParticipantSpec> specs = new ArrayList<>();
            for (int i = 0; i < matched.size(); i++) {
                QueueTicket ticket = matched.get(i);
                TeamId team = (i < matched.size() / 2) ? TeamId.TEAM_A : TeamId.TEAM_B;
                specs.add(participantBuilder.buildHumanParticipant(ticket.userId(), ticket.deckId(), team));
            }
            GameRoom room = gameEngineService.createMatch(request.mode(), request.ranked(), false, false, true, specs);
            LobbyNotificationDto event = new LobbyNotificationDto(
                    "MATCH_FOUND",
                    room.getMatchId(),
                    request.mode(),
                    request.ranked(),
                    "Adversaire trouvé ! Lancement du duel.",
                    Instant.now());
            for (QueueTicket ticket : matched) {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSendToUser(ticket.userId().toString(), "/queue/lobby", event);
                }
            }
            return Map.of("status", "MATCH_FOUND", "matchId", room.getMatchId(), "mode", request.mode());
        }
        return Map.of("status", "QUEUED", "mode", request.mode(), "ranked", request.ranked());
    }

    public synchronized void leaveQueue(UUID userId) {
        Iterator<QueueTicket> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next().userId().equals(userId)) {
                it.remove();
            }
        }
    }

    public synchronized Map<String, Object> getQueueStatus(UUID userId) {
        for (QueueTicket ticket : queue) {
            if (ticket.userId().equals(userId)) {
                return Map.of("queued", true, "mode", ticket.mode(), "ranked", ticket.ranked(), "joinedAt", ticket.joinedAt());
            }
        }
        return Map.of("queued", false);
    }

    private List<QueueTicket> tryPopMatch(GameMode mode, boolean ranked) {
        int needed = mode == GameMode.DUEL_1V1 ? 2 : 4;
        List<QueueTicket> candidates = queue.stream()
                .filter(t -> t.mode() == mode && t.ranked() == ranked)
                .limit(needed)
                .toList();
        if (candidates.size() < needed) {
            return null;
        }
        queue.removeAll(candidates);
        return candidates;
    }
}
