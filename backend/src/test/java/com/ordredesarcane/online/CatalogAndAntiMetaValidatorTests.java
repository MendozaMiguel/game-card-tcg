package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordredesarcane.online.catalog.CardCatalogService;
import com.ordredesarcane.online.catalog.CardDefinition;
import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.deck.AntiMetaDeckValidator;
import com.ordredesarcane.online.deck.DeckValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class CatalogAndAntiMetaValidatorTests {

    private static CardCatalogService catalogService;
    private static AntiMetaDeckValidator validator;

    @BeforeAll
    static void setUpCatalog() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        catalogService = CardCatalogService.fromClasspathResource(mapper);
        validator = new AntiMetaDeckValidator(catalogService);
    }

    @Test
    void officialCatalogContainsExactly120CardsAndMatchesFlywaySeedSql() throws IOException {
        List<CardDefinition> allCards = catalogService.findAll();
        assertThat(allCards).hasSize(120);
        assertThat(allCards).extracting(CardDefinition::cardId).doesNotHaveDuplicates();
        assertThat(allCards).extracting(CardDefinition::name).doesNotHaveDuplicates();

        String seedSql = new ClassPathResource("db/migration/V3__seed_120_cards.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (CardDefinition card : allCards) {
            assertThat(seedSql).contains("'" + card.cardId() + "'");
            assertThat(card.abilitiesScript()).isNotEmpty();
        }
    }

    @Test
    void allFourStarterDecksPassStrictAntiMetaValidator() {
        for (Faction faction : Faction.values()) {
            List<String> starterIds = catalogService.getStarterDeckCards(faction).stream()
                    .map(CardDefinition::cardId)
                    .toList();

            DeckValidationResult result = validator.validateCardIds(starterIds);
            assertThat(result.valid())
                    .as("Starter deck %s violations: %s", faction, result.violations())
                    .isTrue();
            assertThat(result.stats().totalCards()).isEqualTo(30);
            assertThat(result.stats().standardMonsterCount()).isEqualTo(7);
            assertThat(result.stats().epicMonsterCount()).isEqualTo(3);
            assertThat(result.stats().familiarCount()).isEqualTo(10);
            assertThat(result.stats().spellCategoryCount()).isEqualTo(10);
            assertThat(result.stats().cost1To2Count()).isEqualTo(14);
            assertThat(result.stats().cost3To4Count()).isEqualTo(12);
            assertThat(result.stats().cost5PlusCount()).isEqualTo(4);
            assertThat(result.stats().rampCount()).isEqualTo(3);
        }
    }

    @Test
    void legalTwoFactionHybridDeckPasses15Plus15WithBothFactionEpics() {
        List<String> hybrid = List.of(
                // 15 FIRE (5M [3 std + 2 epic], 5F, 5S) -> 7 c1-2, 6 c3-4, 2 c5, 2 ramp
                "FIRE_M_01", "FIRE_M_03", "FIRE_M_05", "FIRE_M_08", "FIRE_M_10",
                "FIRE_F_01", "FIRE_F_02", "FIRE_F_05", "FIRE_F_06", "FIRE_F_10",
                "FIRE_S_02", "FIRE_S_04", "FIRE_S_07", "FIRE_S_08", "FIRE_S_09",
                // 15 ICE (5M [4 std + 1 epic], 5F, 5S) -> 7 c1-2, 6 c3-4, 2 c5, 1 ramp
                "ICE_M_01", "ICE_M_02", "ICE_M_04", "ICE_M_06", "ICE_M_10",
                "ICE_F_01", "ICE_F_03", "ICE_F_04", "ICE_F_06", "ICE_F_10",
                "ICE_S_01", "ICE_S_05", "ICE_S_04", "ICE_S_07", "ICE_S_10"
        );

        DeckValidationResult result = validator.validateCardIds(hybrid);
        assertThat(result.valid())
                .as("Hybrid 15/15 violations: %s", result.violations())
                .isTrue();
        assertThat(result.stats().factionCounts()).containsEntry(Faction.FIRE, 15).containsEntry(Faction.ICE, 15);
    }

    @Test
    void singletonLawRejectsDuplicateCard() {
        List<String> fireCards = new ArrayList<>(catalogService.getStarterDeckCards(Faction.FIRE).stream()
                .map(CardDefinition::cardId)
                .toList());
        // Replace FIRE_M_04 (Cost 2 Standard Monster) with duplicate FIRE_M_03 (Cost 2 Standard Monster)
        int idx = fireCards.indexOf("FIRE_M_04");
        fireCards.set(idx, "FIRE_M_03");

        DeckValidationResult result = validator.validateCardIds(fireCards);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Singleton"));
    }

    @Test
    void allegianceLawRejectsThreeFactionsAndUnauthorizedEpicsOrUltimates() {
        // 16 Fire + 13 Ice + 1 Shadow (same cost/type swap: ICE_M_01 (1) -> SHADOW_M_01 (1))
        List<String> threeFactions = List.of(
                "FIRE_M_01", "FIRE_M_03", "FIRE_M_05", "FIRE_M_08", "FIRE_M_10",
                "FIRE_F_01", "FIRE_F_02", "FIRE_F_05", "FIRE_F_06", "FIRE_F_10",
                "FIRE_S_02", "FIRE_S_04", "FIRE_S_07", "FIRE_S_08", "FIRE_S_09",
                "FIRE_S_03",
                "SHADOW_M_01", "ICE_M_02", "ICE_M_04", "ICE_M_06", "ICE_M_07",
                "ICE_F_01", "ICE_F_03", "ICE_F_04", "ICE_F_06", "ICE_F_10",
                "ICE_S_01", "ICE_S_04", "ICE_S_07", "ICE_S_10"
        );
        DeckValidationResult threeResult = validator.validateCardIds(threeFactions);
        assertThat(threeResult.valid()).isFalse();
        assertThat(threeResult.violations()).anyMatch(v -> v.contains("maximum 2 Factions"));

        // 16 Fire + 14 Ice containing ICE_S_10 (Ultimate Cost 5) while Ice only has 14 cards (< 15)
        List<String> disloyalUltimate = List.of(
                "FIRE_M_01", "FIRE_M_03", "FIRE_M_05", "FIRE_M_08", "FIRE_M_10",
                "FIRE_F_01", "FIRE_F_02", "FIRE_F_05", "FIRE_F_06", "FIRE_F_10",
                "FIRE_S_02", "FIRE_S_04", "FIRE_S_07", "FIRE_S_08", "FIRE_S_09",
                "FIRE_S_03",
                "ICE_M_01", "ICE_M_02", "ICE_M_04", "ICE_M_06", "ICE_M_07",
                "ICE_F_01", "ICE_F_03", "ICE_F_04", "ICE_F_06", "ICE_F_10",
                "ICE_S_01", "ICE_S_04", "ICE_S_07", "ICE_S_10"
        );
        DeckValidationResult disloyalResult = validator.validateCardIds(disloyalUltimate);
        assertThat(disloyalResult.valid()).isFalse();
        assertThat(disloyalResult.violations()).anyMatch(v -> v.contains("au moins 15 cartes de la faction ICE"));
    }

    @Test
    void rampCapRejectsMoreThanThreeRampCards() {
        // 15 Fire (includes FIRE_F_05, FIRE_F_09, FIRE_S_01 = 3 ramp) + 15 Ice (includes ICE_F_02 = 4th ramp)
        List<String> fourRamp = List.of(
                "FIRE_M_01", "FIRE_M_03", "FIRE_M_05", "FIRE_M_08", "FIRE_M_10",
                "FIRE_F_01", "FIRE_F_05", "FIRE_F_06", "FIRE_F_09", "FIRE_F_10",
                "FIRE_S_01", "FIRE_S_04", "FIRE_S_07", "FIRE_S_08", "FIRE_S_09",
                "ICE_M_01", "ICE_M_02", "ICE_M_04", "ICE_M_06", "ICE_M_10",
                "ICE_F_02", "ICE_F_03", "ICE_F_04", "ICE_F_06", "ICE_F_07",
                "ICE_S_02", "ICE_S_04", "ICE_S_05", "ICE_S_07", "ICE_S_10"
        );

        DeckValidationResult result = validator.validateCardIds(fourRamp);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Plafond Ramp"));
    }

    @Test
    void structureAndCurveRejectsInvalidCategoriesAndEnergyCurve() {
        List<String> modified = new ArrayList<>(catalogService.getStarterDeckCards(Faction.LIGHT).stream()
                .map(CardDefinition::cardId)
                .toList());
        // Swap a Cost-3 Light Spell (LIGHT_S_04) with a Cost-2 Light Standard Monster (LIGHT_M_03? already in deck -> LIGHT_S_02 vs ICE_F_03)
        // Swap LIGHT_S_04 (Cost 3 Spell) with ICE_F_01 (Cost 1 Familiar) -> breaks 10F/10S AND 14/12/4!
        modified.set(modified.indexOf("LIGHT_S_04"), "ICE_F_01");

        DeckValidationResult result = validator.validateCardIds(modified);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("10 Familiers"));
        assertThat(result.violations()).anyMatch(v -> v.contains("10 Sorts"));
        assertThat(result.violations()).anyMatch(v -> v.contains("Courbe d'Énergie"));
    }

    @Test
    void catalogFilterSupportsFactionTypeCostAndKeyword() {
        assertThat(catalogService.filter(Faction.LIGHT, CardType.ARTIFACT, null, null))
                .extracting(CardDefinition::cardId)
                .containsExactly("LIGHT_S_05");
        assertThat(catalogService.filter(null, null, null, "GARDIEN"))
                .extracting(CardDefinition::cardId)
                .containsExactly("LIGHT_M_02");
        assertThat(catalogService.filter(Faction.FIRE, CardType.MONSTER, 5, null))
                .hasSize(2);
    }
}
