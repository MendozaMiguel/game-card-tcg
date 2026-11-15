package com.ordredesarcane.online.game.ws;

import com.ordredesarcane.online.game.dto.PlayerGameView;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameRestController {

    private final GameEngineService gameEngineService;

    public GameRestController(GameEngineService gameEngineService) {
        this.gameEngineService = gameEngineService;
    }

    @GetMapping("/active")
    public Map<String, Object> getActiveMatch(@AuthenticationPrincipal PlayerPrincipal principal) {
        return gameEngineService.findActiveMatchForUser(principal.userId())
                .<Map<String, Object>>map(id -> Map.of("activeMatchId", id))
                .orElseGet(Map::of);
    }

    @GetMapping("/{matchId}/state")
    public PlayerGameView getGameState(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID matchId) {
        return gameEngineService.getGameViewForUser(matchId, principal.userId());
    }
}
