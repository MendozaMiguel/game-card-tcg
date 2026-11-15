package com.ordredesarcane.online.match;

import com.ordredesarcane.online.match.dto.TournamentDtos.CreateTournamentRequest;
import com.ordredesarcane.online.match.dto.TournamentDtos.JoinTournamentRequest;
import com.ordredesarcane.online.match.dto.TournamentDtos.TournamentDto;
import com.ordredesarcane.online.security.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping
    public List<TournamentDto> listTournaments() {
        return tournamentService.listTournaments();
    }

    @PostMapping
    public ResponseEntity<TournamentDto> createTournament(@Valid @RequestBody CreateTournamentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tournamentService.createTournament(request));
    }

    @PostMapping("/{tournamentId}/join")
    public TournamentDto joinTournament(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID tournamentId,
            @Valid @RequestBody JoinTournamentRequest request) {
        return tournamentService.joinTournament(principal.userId(), tournamentId, request);
    }
}
