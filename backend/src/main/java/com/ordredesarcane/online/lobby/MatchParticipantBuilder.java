package com.ordredesarcane.online.lobby;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.deck.DeckService;
import com.ordredesarcane.online.game.engine.GameEngineService.MatchParticipantSpec;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.TeamId;
import com.ordredesarcane.online.user.ProfileService;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MatchParticipantBuilder {

    private static final Faction[] FACTIONS = Faction.values();
    private final DeckService deckService;
    private final ProfileService profileService;
    private final CardCatalogService cardCatalogService;
    private final SecureRandom random = new SecureRandom();

    public MatchParticipantBuilder(
            DeckService deckService,
            ProfileService profileService,
            CardCatalogService cardCatalogService) {
        this.deckService = deckService;
        this.profileService = profileService;
        this.cardCatalogService = cardCatalogService;
    }

    public MatchParticipantSpec buildHumanParticipant(UUID userId, UUID deckId, TeamId teamId) {
        List<CardDefinition> cards = deckService.resolveAndVerifyDeckForMatch(userId, deckId);
        UserProfileDto profile = profileService.getProfile(userId);
        return new MatchParticipantSpec(
                userId,
                profile.username(),
                false,
                teamId,
                cards,
                profile.equippedCardBack(),
                profile.equippedBoardSkin(),
                profile.equippedPet(),
                profile.equippedEnergySkin(),
                profile.equippedAvatar(),
                profile.equippedLeagueFrame(),
                profile.equippedTitle(),
                profile.equippedFinisher());
    }

    public MatchParticipantSpec buildBotParticipant(String botName, TeamId teamId, Faction preferredFaction) {
        return buildBotParticipant(botName, teamId, preferredFaction, AiDifficulty.MEDIUM);
    }

    public MatchParticipantSpec buildBotParticipant(
            String botName,
            TeamId teamId,
            Faction preferredFaction,
            AiDifficulty difficulty) {
        AiDifficulty resolvedDifficulty = AiDifficulty.resolveOrDefault(difficulty);
        Faction faction = preferredFaction != null ? preferredFaction : FACTIONS[random.nextInt(FACTIONS.length)];
        List<CardDefinition> cards = cardCatalogService.getStarterDeckCards(faction);
        return new MatchParticipantSpec(
                UUID.randomUUID(),
                botName + " · " + resolvedDifficulty.displayLabel() + " (" + faction.name() + ")",
                true,
                teamId,
                cards,
                "card_back_default",
                "board_sanctuary",
                "pet_none",
                "energy_arcane",
                "avatar_automaton",
                "frame_mage",
                "Automate · " + resolvedDifficulty.displayLabel(),
                "finisher_arcane_burst",
                resolvedDifficulty);
    }
}
