package com.ordredesarcane.online.deck;

import com.ordredesarcane.online.deck.dto.DeckDto;
import com.ordredesarcane.online.deck.dto.SaveDeckRequest;
import com.ordredesarcane.online.deck.dto.ValidateDeckRequest;
import com.ordredesarcane.online.security.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    public List<DeckDto> listMyDecks(@AuthenticationPrincipal PlayerPrincipal principal) {
        return deckService.getUserDecks(principal.userId());
    }

    @PostMapping("/validate")
    public DeckValidationResult validateDeck(@Valid @RequestBody ValidateDeckRequest request) {
        return deckService.validateCandidate(request.cardIds());
    }

    @PostMapping
    public ResponseEntity<DeckDto> createDeck(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody SaveDeckRequest request) {
        DeckDto created = deckService.createDeck(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{deckId}")
    public DeckDto updateDeck(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID deckId,
            @Valid @RequestBody SaveDeckRequest request) {
        return deckService.updateDeck(principal.userId(), deckId, request);
    }

    @PatchMapping("/{deckId}/favorite")
    public DeckDto toggleFavorite(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID deckId) {
        return deckService.toggleFavorite(principal.userId(), deckId);
    }

    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> deleteDeck(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID deckId) {
        deckService.deleteDeck(principal.userId(), deckId);
        return ResponseEntity.noContent().build();
    }
}
