package com.ordredesarcane.online.game.ws;

import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Service
public class SessionPresenceService {

    private final ConcurrentHashMap<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final GameEngineService gameEngineService;

    public SessionPresenceService(GameEngineService gameEngineService) {
        this.gameEngineService = gameEngineService;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = extractUserId(accessor.getUser());
        String sessionId = accessor.getSessionId();
        if (userId == null || sessionId == null) {
            return;
        }
        Set<String> sessions = userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        boolean wasOffline = sessions.isEmpty();
        sessions.add(sessionId);
        if (wasOffline) {
            gameEngineService.onPlayerReconnected(userId);
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = extractUserId(accessor.getUser());
        String sessionId = accessor.getSessionId();
        if (userId == null || sessionId == null) {
            return;
        }
        Set<String> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            userSessions.remove(userId, sessions);
            gameEngineService.onPlayerDisconnected(userId);
        }
    }

    public boolean isOnline(UUID userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof PlayerPrincipal pp) {
            return pp.userId();
        }
        if (principal != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
