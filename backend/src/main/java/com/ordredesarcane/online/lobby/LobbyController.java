package com.ordredesarcane.online.lobby;

import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.lobby.dto.CreateCustomRoomRequest;
import com.ordredesarcane.online.lobby.dto.CustomRoomDto;
import com.ordredesarcane.online.lobby.dto.JoinCustomRoomRequest;
import com.ordredesarcane.online.lobby.dto.MatchSummaryDto;
import com.ordredesarcane.online.lobby.dto.QueueJoinRequest;
import com.ordredesarcane.online.lobby.dto.StartAiMatchRequest;
import com.ordredesarcane.online.match.MatchEntity;
import com.ordredesarcane.online.match.MatchRepository;
import com.ordredesarcane.online.match.MatchSource;
import com.ordredesarcane.online.security.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lobby")
public class LobbyController {

    private final MatchParticipantBuilder participantBuilder;
    private final GameEngineService gameEngineService;
    private final MatchmakingService matchmakingService;
    private final CustomRoomService customRoomService;
    private final MatchRepository matchRepository;

    public LobbyController(
            MatchParticipantBuilder participantBuilder,
            GameEngineService gameEngineService,
            MatchmakingService matchmakingService,
            CustomRoomService customRoomService,
            MatchRepository matchRepository) {
        this.participantBuilder = participantBuilder;
        this.gameEngineService = gameEngineService;
        this.matchmakingService = matchmakingService;
        this.customRoomService = customRoomService;
        this.matchRepository = matchRepository;
    }

    @PostMapping("/ai-match")
    public Map<String, Object> startAiMatch(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody StartAiMatchRequest request) {
        AiDifficulty difficulty = AiDifficulty.resolveOrDefault(request.difficulty());
        List<MatchParticipantSpec> specs = new ArrayList<>();
        specs.add(participantBuilder.buildHumanParticipant(principal.userId(), request.deckId(), TeamId.TEAM_A));
        if (request.mode() == GameMode.TEAM_2V2) {
            AiDifficulty allyDifficulty = difficulty == AiDifficulty.HARD ? AiDifficulty.MEDIUM : difficulty;
            specs.add(participantBuilder.buildBotParticipant("Allié Automate", TeamId.TEAM_A, null, allyDifficulty));
            specs.add(participantBuilder.buildBotParticipant("Mage Automate Alpha", TeamId.TEAM_B, request.botFaction(), difficulty));
            specs.add(participantBuilder.buildBotParticipant("Mage Automate Beta", TeamId.TEAM_B, null, difficulty));
        } else {
            specs.add(participantBuilder.buildBotParticipant("Mage Automate", TeamId.TEAM_B, request.botFaction(), difficulty));
        }
        GameRoom room = gameEngineService.createMatch(request.mode(), false, true, false, true, specs);
        return Map.of("matchId", room.getMatchId(), "mode", room.getMode(), "difficulty", difficulty);
    }

    @PostMapping("/queue/join")
    public Map<String, Object> joinQueue(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody QueueJoinRequest request) {
        return matchmakingService.joinQueue(principal.userId(), request);
    }

    @DeleteMapping("/queue")
    public Map<String, Object> leaveQueue(@AuthenticationPrincipal PlayerPrincipal principal) {
        matchmakingService.leaveQueue(principal.userId());
        return Map.of("queued", false);
    }

    @GetMapping("/queue/status")
    public Map<String, Object> queueStatus(@AuthenticationPrincipal PlayerPrincipal principal) {
        return matchmakingService.getQueueStatus(principal.userId());
    }

    @GetMapping("/custom-rooms")
    public List<CustomRoomDto> listCustomRooms() {
        return customRoomService.listJoinableRooms();
    }

    @PostMapping("/custom-rooms")
    public CustomRoomDto createCustomRoom(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody CreateCustomRoomRequest request) {
        return customRoomService.createRoom(principal.userId(), request);
    }

    @PostMapping("/custom-rooms/{roomCode}/join")
    public CustomRoomDto joinCustomRoom(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomCode,
            @Valid @RequestBody JoinCustomRoomRequest request) {
        return customRoomService.joinRoom(principal.userId(), roomCode, request);
    }

    @PostMapping("/custom-rooms/{roomCode}/start")
    public CustomRoomDto startCustomRoom(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomCode,
            @RequestParam(defaultValue = "false") boolean fillWithBots) {
        return customRoomService.startRoom(principal.userId(), roomCode, fillWithBots);
    }

    @GetMapping("/matches/history")
    public List<MatchSummaryDto> recentMatches() {
        return matchRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(LobbyController::toMatchSummaryDto)
                .toList();
    }

    public static MatchSummaryDto toMatchSummaryDto(MatchEntity match) {
        Map<String, Object> summary = match.getMatchSummary() != null ? match.getMatchSummary() : Map.of();
        MatchSource source = match.getMatchSource() != null
                ? match.getMatchSource()
                : MatchSource.resolve(match.isRanked(), match.isAiMatch(), match.isCustomRoom(), match.getTournamentId());
        return new MatchSummaryDto(
                match.getMatchId(),
                match.getMode(),
                match.isRanked(),
                match.isAiMatch(),
                match.isCustomRoom(),
                source,
                match.getTournamentId(),
                match.getTournamentName(),
                match.getTeamAIds(),
                match.getTeamBIds(),
                readStringList(summary.get("teamANames")),
                readStringList(summary.get("teamBNames")),
                readInt(summary.get("teamALp")),
                readInt(summary.get("teamBLp")),
                match.getWinnerTeam(),
                match.getEndReason(),
                match.getTurnCount(),
                match.getCreatedAt(),
                match.getFinishedAt());
    }

    private static List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Integer readInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
