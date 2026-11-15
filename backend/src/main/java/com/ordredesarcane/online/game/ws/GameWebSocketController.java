package com.ordredesarcane.online.game.ws;

import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.dto.GameErrorEvent;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class GameWebSocketController {

    private final GameEngineService gameEngineService;

    public GameWebSocketController(GameEngineService gameEngineService) {
        this.gameEngineService = gameEngineService;
    }

    @MessageMapping("/game.action")
    public void handleGameAction(@Valid @Payload GameActionCommand command, Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Session WebSocket non authentifiée.");
        }
        UUID userId = principal instanceof PlayerPrincipal pp
                ? pp.userId()
                : UUID.fromString(principal.getName());
        gameEngineService.processAction(userId, command);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public GameErrorEvent handleMessageException(Exception ex) {
        return new GameErrorEvent(
                null,
                "WS_VALIDATION_OR_RULE_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "Action invalide.",
                Instant.now());
    }
}
