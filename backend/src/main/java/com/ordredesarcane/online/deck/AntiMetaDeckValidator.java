package com.ordredesarcane.online.deck;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AntiMetaDeckValidator {

    public static final int REQUIRED_DECK_SIZE = 30;
    public static final int REQUIRED_MONSTERS = 10;
    public static final int MIN_STANDARD_MONSTERS = 7;
    public static final int MAX_EPIC_MONSTERS = 3;
    public static final int REQUIRED_FAMILIARS = 10;
    public static final int REQUIRED_SPELLS_GROUP = 10;
    public static final int REQUIRED_COST_1_2 = 14;
    public static final int REQUIRED_COST_3_4 = 12;
    public static final int REQUIRED_COST_5_PLUS = 4;
    public static final int MAX_FACTIONS = 2;
    public static final int LOYALTY_MIN_CARDS = 15;
    public static final int MAX_RAMP_CARDS = 3;

    private final CardCatalogService cardCatalogService;

    public AntiMetaDeckValidator(CardCatalogService cardCatalogService) {
        this.cardCatalogService = cardCatalogService;
    }

    public DeckValidationResult validateCardIds(List<String> cardIds) {
        List<String> violations = new ArrayList<>();
        List<CardDefinition> resolvedCards = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        boolean duplicateDetected = false;

        if (cardIds == null || cardIds.isEmpty()) {
            violations.add("Le deck doit contenir exactement 30 cartes.");
            return new DeckValidationResult(false, List.copyOf(violations), emptyStats());
        }

        for (String cardId : cardIds) {
            if (cardId == null || cardId.isBlank()) {
                violations.add("Identifiant de carte vide détecté.");
                continue;
            }
            if (!seenIds.add(cardId)) {
                duplicateDetected = true;
            }
            Optional<CardDefinition> defOpt = cardCatalogService.findById(cardId);
            if (defOpt.isEmpty()) {
                violations.add("Carte inconnue dans le catalogue officiel : " + cardId);
                continue;
            }
            CardDefinition def = defOpt.get();
            if (!seenNames.add(def.name())) {
                duplicateDetected = true;
            }
            resolvedCards.add(def);
        }

        if (duplicateDetected) {
            violations.add("Loi d'Unicité (Singleton) violée : maximum 1 seul exemplaire de chaque carte.");
        }

        return validateResolvedCards(resolvedCards, violations, cardIds.size());
    }

    public DeckValidationResult validateDefinitions(List<CardDefinition> cards) {
        List<String> violations = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        boolean duplicateDetected = false;

        for (CardDefinition def : cards) {
            if (!seenIds.add(def.cardId()) || !seenNames.add(def.name())) {
                duplicateDetected = true;
            }
        }
        if (duplicateDetected) {
            violations.add("Loi d'Unicité (Singleton) violée : maximum 1 seul exemplaire de chaque carte.");
        }
        return validateResolvedCards(cards, violations, cards.size());
    }

    private DeckValidationResult validateResolvedCards(
            List<CardDefinition> cards,
            List<String> violations,
            int submittedCount) {

        int monsterCount = 0;
        int standardMonsterCount = 0;
        int epicMonsterCount = 0;
        int familiarCount = 0;
        int spellGroupCount = 0;
        int cost1To2 = 0;
        int cost3To4 = 0;
        int cost5Plus = 0;
        int rampCount = 0;
        Map<Faction, Integer> factionCounts = new EnumMap<>(Faction.class);

        for (CardDefinition card : cards) {
            factionCounts.merge(card.faction(), 1, Integer::sum);

            if (card.cardType() == CardType.MONSTER) {
                monsterCount++;
                if (card.isEpic()) {
                    epicMonsterCount++;
                } else {
                    standardMonsterCount++;
                }
            } else if (card.cardType() == CardType.FAMILIAR) {
                familiarCount++;
            } else {
                spellGroupCount++;
            }

            if (card.energyCost() <= 2) {
                cost1To2++;
            } else if (card.energyCost() <= 4) {
                cost3To4++;
            } else {
                cost5Plus++;
            }

            if (card.isRamp()) {
                rampCount++;
            }
        }

        // Loi 1 : Structure & Courbe d'Énergie
        if (submittedCount != REQUIRED_DECK_SIZE || cards.size() != REQUIRED_DECK_SIZE) {
            violations.add("Structure invalide : le deck doit contenir exactement 30 cartes (actuel : "
                    + submittedCount + ").");
        }
        if (monsterCount != REQUIRED_MONSTERS) {
            violations.add("Structure invalide : exactement 10 Monstres requis (actuel : " + monsterCount + ").");
        }
        if (epicMonsterCount > MAX_EPIC_MONSTERS) {
            violations.add("Structure invalide : maximum 3 Monstres Épiques autorisés (actuel : "
                    + epicMonsterCount + ").");
        }
        if (monsterCount == REQUIRED_MONSTERS && standardMonsterCount < MIN_STANDARD_MONSTERS) {
            violations.add("Structure invalide : minimum 7 Monstres Standards requis (actuel : "
                    + standardMonsterCount + ").");
        }
        if (familiarCount != REQUIRED_FAMILIARS) {
            violations.add("Structure invalide : exactement 10 Familiers requis (actuel : " + familiarCount + ").");
        }
        if (spellGroupCount != REQUIRED_SPELLS_GROUP) {
            violations.add("Structure invalide : exactement 10 Sorts/Malédictions/Artefacts requis (actuel : "
                    + spellGroupCount + ").");
        }
        if (cost1To2 != REQUIRED_COST_1_2 || cost3To4 != REQUIRED_COST_3_4 || cost5Plus != REQUIRED_COST_5_PLUS) {
            violations.add(String.format(
                    "Courbe d'Énergie invalide : requis 14/12/4 (Coût 1-2 / 3-4 / 5+), actuel : %d/%d/%d.",
                    cost1To2, cost3To4, cost5Plus));
        }

        // Loi 3 : Allégeance (max 2 factions + fidélité 15 cartes pour Épiques ★ & Sorts Ultimes)
        if (factionCounts.size() > MAX_FACTIONS) {
            violations.add("Loi d'Allégeance violée : maximum 2 Factions autorisées par deck (actuel : "
                    + factionCounts.size() + ").");
        }
        for (CardDefinition card : cards) {
            if (card.requiresFactionLoyalty()) {
                int countInFaction = factionCounts.getOrDefault(card.faction(), 0);
                if (countInFaction < LOYALTY_MIN_CARDS) {
                    violations.add(String.format(
                            "Loi d'Allégeance violée : '%s' (%s) requiert au moins 15 cartes de la faction %s (actuel : %d).",
                            card.name(),
                            card.isEpic() ? "Épique" : "Sort Ultime",
                            card.faction(),
                            countInFaction));
                }
            }
        }

        // Loi 4 : Plafond Ramp (max 3 cartes génératrices d'énergie)
        if (rampCount > MAX_RAMP_CARDS) {
            violations.add("Plafond Ramp violé : maximum 3 cartes génératrices d'Énergie autorisées (actuel : "
                    + rampCount + ").");
        }

        List<Map.Entry<Faction, Integer>> sortedFactions = factionCounts.entrySet().stream()
                .sorted(Map.Entry.<Faction, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .toList();

        Faction primary = sortedFactions.isEmpty() ? null : sortedFactions.get(0).getKey();
        Faction secondary = sortedFactions.size() > 1 ? sortedFactions.get(1).getKey() : null;

        DeckStats stats = new DeckStats(
                cards.size(),
                monsterCount,
                standardMonsterCount,
                epicMonsterCount,
                familiarCount,
                spellGroupCount,
                cost1To2,
                cost3To4,
                cost5Plus,
                rampCount,
                Map.copyOf(factionCounts),
                primary,
                secondary);

        return new DeckValidationResult(violations.isEmpty(), List.copyOf(violations), stats);
    }

    private DeckStats emptyStats() {
        return new DeckStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), null, null);
    }
}
