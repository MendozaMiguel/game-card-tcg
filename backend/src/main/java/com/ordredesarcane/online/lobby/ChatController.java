package com.ordredesarcane.online.lobby;

import com.ordredesarcane.online.game.dto.GameErrorEvent;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import com.ordredesarcane.online.lobby.dto.ChatMessageCommand;
import com.ordredesarcane.online.lobby.dto.ChatMessageDto;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.security.RateLimiterService;
import com.ordredesarcane.online.security.XssSanitizer;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final XssSanitizer xssSanitizer;
    private final RateLimiterService rateLimiterService;
    private final GameEngineService gameEngineService;

    public ChatController(
            SimpMessagingTemplate messagingTemplate,
            XssSanitizer xssSanitizer,
            RateLimiterService rateLimiterService,
            GameEngineService gameEngineService) {
        this.messagingTemplate = messagingTemplate;
        this.xssSanitizer = xssSanitizer;
        this.rateLimiterService = rateLimiterService;
        this.gameEngineService = gameEngineService;
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public GameErrorEvent handleChatException(Exception ex) {
        String message = "Message de chat invalide.";
        if (ex instanceof MethodArgumentNotValidException validationEx
                && validationEx.getBindingResult() != null
                && validationEx.getBindingResult().getFieldError() != null) {
            message = validationEx.getBindingResult().getFieldError().getDefaultMessage();
        } else if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }
        return new GameErrorEvent(null, "CHAT_ERROR", message, Instant.now());
    }

    @MessageMapping("/chat.global")
    public void sendGlobalChat(@Valid @Payload ChatMessageCommand command, Principal principal) {
        PlayerPrincipal user = requirePrincipal(principal);
        enforceChatRateLimit(user);
        String sanitized = xssSanitizer.sanitize(command.content(), 300);
        if (sanitized.isBlank()) {
            return;
        }
        ChatMessageDto dto = new ChatMessageDto(
                UUID.randomUUID(),
                null,
                user.userId(),
                user.username(),
                false,
                sanitized,
                Instant.now());
        messagingTemplate.convertAndSend("/topic/chat.global", dto);
    }

    @MessageMapping("/chat.game")
    public void sendGameChat(@Valid @Payload ChatMessageCommand command, Principal principal) {
        PlayerPrincipal user = requirePrincipal(principal);
        if (command.matchId() == null) {
            throw new IllegalArgumentException("matchId obligatoire pour le chat de partie.");
        }
        enforceChatRateLimit(user);
        String sanitized = xssSanitizer.sanitize(command.content(), 300);
        if (sanitized.isBlank()) {
            return;
        }
        GameRoom room = gameEngineService.getRoomOrThrow(command.matchId());
        PlayerState sender = room.getPlayer(user.userId());
        boolean spectator = sender == null && room.getSpectatorIds().contains(user.userId());
        if (sender == null && !spectator) {
            throw new AccessDeniedException("Accès chat de partie refusé.");
        }

        ChatMessageDto dto = new ChatMessageDto(
                UUID.randomUUID(),
                command.matchId(),
                user.userId(),
                user.username(),
                command.teamOnly() && sender != null,
                sanitized,
                Instant.now());

        if (command.teamOnly() && sender != null) {
            for (PlayerState teammate : room.getTeamPlayers(sender.getTeamId())) {
                if (!teammate.isBot()) {
                    messagingTemplate.convertAndSendToUser(teammate.getUserId().toString(), "/queue/chat.team", dto);
                }
            }
        } else {
            messagingTemplate.convertAndSend("/topic/chat.game." + command.matchId(), dto);
        }
    }

    private void enforceChatRateLimit(PlayerPrincipal user) {
        if (!rateLimiterService.allowChatMessage(user.getName())) {
            throw new IllegalArgumentException("Limite chat dépassée (max 1 message/seconde).");
        }
    }

    private PlayerPrincipal requirePrincipal(Principal principal) {
        if (principal instanceof PlayerPrincipal pp) {
            return pp;
        }
        throw new AccessDeniedException("Session non authentifiée.");
    }
}
