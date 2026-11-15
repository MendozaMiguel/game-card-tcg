package com.ordredesarcane.online.deck;

import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.deck.dto.DeckCardDto;
import com.ordredesarcane.online.deck.dto.DeckCardInput;
import com.ordredesarcane.online.deck.dto.DeckDto;
import com.ordredesarcane.online.deck.dto.SaveDeckRequest;
import com.ordredesarcane.online.security.XssSanitizer;
import com.ordredesarcane.online.user.CosmeticType;
import com.ordredesarcane.online.user.UserCosmeticRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardCatalogService cardCatalogService;
    private final AntiMetaDeckValidator validator;
    private final UserCosmeticRepository cosmeticRepository;
    private final XssSanitizer xssSanitizer;

    public DeckService(
            DeckRepository deckRepository,
            CardCatalogService cardCatalogService,
            AntiMetaDeckValidator validator,
            UserCosmeticRepository cosmeticRepository,
            XssSanitizer xssSanitizer) {
        this.deckRepository = deckRepository;
        this.cardCatalogService = cardCatalogService;
        this.validator = validator;
        this.cosmeticRepository = cosmeticRepository;
        this.xssSanitizer = xssSanitizer;
    }

    @Transactional(readOnly = true)
    public List<DeckDto> getUserDecks(UUID userId) {
        return deckRepository.findByUserIdOrderByFavoriteDescUpdatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public DeckValidationResult validateCandidate(List<String> cardIds) {
        return validator.validateCardIds(cardIds);
    }

    @Transactional
    public DeckDto createDeck(UUID userId, SaveDeckRequest request) {
        List<String> cardIds = request.cards().stream().map(DeckCardInput::cardId).toList();
        DeckValidationResult validation = validator.validateCardIds(cardIds);
        if (!validation.valid()) {
            throw new DeckValidationException(validation);
        }
        verifyFoilOwnership(userId, request.cards());

        String safeName = sanitizeDeckName(request.name());
        DeckEntity deck = new DeckEntity(
                userId,
                safeName,
                validation.stats().primaryFaction(),
                validation.stats().secondaryFaction(),
                request.favorite(),
                true,
                false);
        DeckEntity saved = deckRepository.saveAndFlush(deck);

        for (int i = 0; i < request.cards().size(); i++) {
            DeckCardInput input = request.cards().get(i);
            saved.addCard(input.cardId(), input.foil(), i);
        }
        return toDto(deckRepository.save(saved));
    }

    @Transactional
    public DeckDto updateDeck(UUID userId, UUID deckId, SaveDeckRequest request) {
        DeckEntity deck = deckRepository.findByDeckIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck introuvable."));

        List<String> cardIds = request.cards().stream().map(DeckCardInput::cardId).toList();
        DeckValidationResult validation = validator.validateCardIds(cardIds);
        if (!validation.valid()) {
            throw new DeckValidationException(validation);
        }
        verifyFoilOwnership(userId, request.cards());

        deck.setName(sanitizeDeckName(request.name()));
        deck.setPrimaryFaction(validation.stats().primaryFaction());
        deck.setSecondaryFaction(validation.stats().secondaryFaction());
        deck.setFavorite(request.favorite());
        deck.setValidAntimeta(true);
        deck.clearCards();
        deckRepository.saveAndFlush(deck);

        for (int i = 0; i < request.cards().size(); i++) {
            DeckCardInput input = request.cards().get(i);
            deck.addCard(input.cardId(), input.foil(), i);
        }
        return toDto(deckRepository.save(deck));
    }

    @Transactional
    public DeckDto toggleFavorite(UUID userId, UUID deckId) {
        DeckEntity deck = deckRepository.findByDeckIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck introuvable."));
        deck.setFavorite(!deck.isFavorite());
        return toDto(deckRepository.save(deck));
    }

    @Transactional
    public void deleteDeck(UUID userId, UUID deckId) {
        DeckEntity deck = deckRepository.findByDeckIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck introuvable."));
        if (deckRepository.countByUserId(userId) <= 1) {
            throw new IllegalArgumentException("Impossible de supprimer votre dernier deck.");
        }
        deckRepository.delete(deck);
    }

    @Transactional
    public void createStarterDecksForUser(UUID userId) {
        createStarterDeck(userId, "Ordre du Feu", Faction.FIRE, true);
        createStarterDeck(userId, "Citadelle de Glace", Faction.ICE, false);
        createStarterDeck(userId, "Pacte des Ombres", Faction.SHADOW, false);
        createStarterDeck(userId, "Bastion de Lumière", Faction.LIGHT, false);
    }

    @Transactional(readOnly = true)
    public List<CardDefinition> resolveAndVerifyDeckForMatch(UUID userId, UUID deckId) {
        DeckEntity deck = deckRepository.findByDeckIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck sélectionné introuvable."));
        List<String> cardIds = deck.getCards().stream().map(DeckCardEntity::getCardId).toList();
        DeckValidationResult validation = validator.validateCardIds(cardIds);
        if (!validation.valid()) {
            throw new DeckValidationException(validation);
        }
        Map<String, CardDefinition> resolved = cardCatalogService.resolveByIds(cardIds);
        return cardIds.stream().map(resolved::get).toList();
    }

    private void createStarterDeck(UUID userId, String name, Faction faction, boolean favorite) {
        List<CardDefinition> starterCards = cardCatalogService.getStarterDeckCards(faction);
        DeckValidationResult validation = validator.validateDefinitions(starterCards);
        if (!validation.valid()) {
            throw new IllegalStateException("Deck de départ invalide pour " + faction + ": " + validation.violations());
        }
        DeckEntity entity = deckRepository.saveAndFlush(new DeckEntity(
                userId, name, faction, null, favorite, true, true));
        for (int i = 0; i < starterCards.size(); i++) {
            entity.addCard(starterCards.get(i).cardId(), false, i);
        }
        deckRepository.save(entity);
    }

    private void verifyFoilOwnership(UUID userId, List<DeckCardInput> inputs) {
        for (DeckCardInput input : inputs) {
            if (input.foil()) {
                boolean unlocked = cosmeticRepository.existsByUserIdAndCosmeticTypeAndItemId(
                        userId, CosmeticType.CARD_FOIL, input.cardId());
                if (!unlocked) {
                    throw new IllegalArgumentException(
                            "Version Foil non débloquée pour la carte : " + input.cardId());
                }
            }
        }
    }

    private String sanitizeDeckName(String rawName) {
        String safe = xssSanitizer.sanitize(rawName, 64);
        if (safe.length() < 2) {
            throw new IllegalArgumentException("Nom de deck invalide.");
        }
        return safe;
    }

    private DeckDto toDto(DeckEntity entity) {
        List<String> cardIds = entity.getCards().stream().map(DeckCardEntity::getCardId).toList();
        Map<String, CardDefinition> catalog = cardCatalogService.resolveByIds(cardIds);
        List<DeckCardDto> cardDtos = entity.getCards().stream()
                .map(c -> new DeckCardDto(catalog.get(c.getCardId()), c.isFoil(), c.getPosition()))
                .toList();
        DeckValidationResult validation = validator.validateCardIds(cardIds);
        return new DeckDto(
                entity.getDeckId(),
                entity.getName(),
                entity.getPrimaryFaction(),
                entity.getSecondaryFaction(),
                entity.isFavorite(),
                validation.valid(),
                entity.isStarter(),
                entity.getUpdatedAt(),
                cardDtos,
                validation);
    }
}
