package com.ordredesarcane.online.match;

import com.ordredesarcane.online.api.ApiConflictException;
import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.deck.DeckEntity;
import com.ordredesarcane.online.deck.DeckRepository;
import com.ordredesarcane.online.deck.DeckService;
import com.ordredesarcane.online.match.dto.TournamentDtos.CreateTournamentRequest;
import com.ordredesarcane.online.match.dto.TournamentDtos.JoinTournamentRequest;
import com.ordredesarcane.online.match.dto.TournamentDtos.TournamentDto;
import com.ordredesarcane.online.match.dto.TournamentDtos.TournamentParticipantDto;
import com.ordredesarcane.online.security.XssSanitizer;
import com.ordredesarcane.online.user.UserEntity;
import com.ordredesarcane.online.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final DeckRepository deckRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final XssSanitizer xssSanitizer;

    public TournamentService(
            TournamentRepository tournamentRepository,
            TournamentParticipantRepository participantRepository,
            DeckRepository deckRepository,
            DeckService deckService,
            UserRepository userRepository,
            XssSanitizer xssSanitizer) {
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.deckRepository = deckRepository;
        this.deckService = deckService;
        this.userRepository = userRepository;
        this.xssSanitizer = xssSanitizer;
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> listTournaments() {
        return tournamentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TournamentDto createTournament(CreateTournamentRequest request) {
        if (request.maxPlayers() != 8 && request.maxPlayers() != 16) {
            throw new IllegalArgumentException("Un tournoi officiel Conquest Bo3 exige exactement 8 ou 16 joueurs.");
        }
        String safeName = xssSanitizer.sanitize(request.name(), 80);
        TournamentEntity entity = tournamentRepository.save(new TournamentEntity(safeName, request.maxPlayers(), "OPEN"));
        return toDto(entity);
    }

    @Transactional
    public TournamentDto joinTournament(UUID userId, UUID tournamentId, JoinTournamentRequest request) {
        TournamentEntity tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournoi introuvable."));
        if (!"OPEN".equals(tournament.getStatus())) {
            throw new ApiConflictException("Les inscriptions sont fermées pour ce tournoi.");
        }
        if (request.deck1Id().equals(request.deck2Id())) {
            throw new IllegalArgumentException("Sélectionnez 2 decks distincts pour le format Conquest Bo3.");
        }

        deckService.resolveAndVerifyDeckForMatch(userId, request.deck1Id());
        deckService.resolveAndVerifyDeckForMatch(userId, request.deck2Id());
        DeckEntity deck1 = deckRepository.findByDeckIdAndUserId(request.deck1Id(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck 1 introuvable."));
        DeckEntity deck2 = deckRepository.findByDeckIdAndUserId(request.deck2Id(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck 2 introuvable."));

        if (deck1.getPrimaryFaction() == deck2.getPrimaryFaction()) {
            throw new IllegalArgumentException(
                    "Règle Conquest Anti-Méta : les 2 decks inscrits doivent appartenir à des factions principales différentes.");
        }

        TournamentParticipantId pk = new TournamentParticipantId(tournamentId, userId);
        if (participantRepository.existsById(pk)) {
            throw new ApiConflictException("Vous êtes déjà inscrit à ce tournoi.");
        }

        long currentCount = participantRepository.countByIdTournamentId(tournamentId);
        if (currentCount >= tournament.getMaxPlayers()) {
            throw new ApiConflictException("Le tournoi est complet.");
        }

        int seed = (int) currentCount + 1;
        participantRepository.save(new TournamentParticipantEntity(
                tournamentId, userId, request.deck1Id(), request.deck2Id(), seed));

        if (seed >= tournament.getMaxPlayers()) {
            startBracket(tournament);
        }
        return toDto(tournament);
    }

    private void startBracket(TournamentEntity tournament) {
        List<TournamentParticipantEntity> participants =
                participantRepository.findByIdTournamentIdOrderBySeedAsc(tournament.getTournamentId());
        List<Map<String, Object>> round1Matches = new ArrayList<>();
        for (int i = 0; i < participants.size() - 1; i += 2) {
            TournamentParticipantEntity p1 = participants.get(i);
            TournamentParticipantEntity p2 = participants.get(i + 1);
            Map<String, Object> pairing = new LinkedHashMap<>();
            pairing.put("round", 1);
            pairing.put("player1Id", p1.getId().getUserId());
            pairing.put("player2Id", p2.getId().getUserId());
            pairing.put("format", "CONQUEST_BO3");
            pairing.put("p1Wins", 0);
            pairing.put("p2Wins", 0);
            round1Matches.add(pairing);
        }
        tournament.setStatus("IN_PROGRESS");
        tournament.setBracketData(Map.of("format", "CONQUEST_BO3", "rounds", Map.of("1", round1Matches)));
        tournamentRepository.save(tournament);
    }

    private TournamentDto toDto(TournamentEntity entity) {
        List<TournamentParticipantDto> participants = participantRepository
                .findByIdTournamentIdOrderBySeedAsc(entity.getTournamentId()).stream()
                .map(p -> new TournamentParticipantDto(
                        p.getId().getUserId(),
                        userRepository.findById(p.getId().getUserId()).map(UserEntity::getUsername).orElse("Mage"),
                        p.getDeck1Id(),
                        p.getDeck2Id(),
                        p.getSeed(),
                        p.isEliminated()))
                .toList();
        return new TournamentDto(
                entity.getTournamentId(),
                entity.getName(),
                entity.getMaxPlayers(),
                entity.getStatus(),
                entity.getBracketData(),
                participants,
                entity.getCreatedAt(),
                entity.getFinishedAt());
    }
}
