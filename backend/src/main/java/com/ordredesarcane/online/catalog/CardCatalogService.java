package com.ordredesarcane.online.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CardCatalogService {

    private final CardCatalogRepository repository;
    private volatile Map<String, CardDefinition> cache;

    @Autowired
    public CardCatalogService(CardCatalogRepository repository) {
        this.repository = repository;
    }

    private CardCatalogService(List<CardDefinition> seedCards) {
        this.repository = null;
        this.cache = indexCards(seedCards);
    }

    public static CardCatalogService fromClasspathResource(JsonMapper jsonMapper) {
        var resource = new ClassPathResource("data/cards-catalog-120.json");
        try (InputStream in = resource.getInputStream()) {
            List<CardDefinition> cards = jsonMapper.readValue(
                    in, new TypeReference<List<CardDefinition>>() {});
            return new CardCatalogService(cards);
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de charger le catalogue des 120 cartes", ex);
        }
    }

    public List<CardDefinition> findAll() {
        return List.copyOf(getCatalogMap().values());
    }

    public Optional<CardDefinition> findById(String cardId) {
        if (cardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getCatalogMap().get(cardId));
    }

    public List<CardDefinition> filter(Faction faction, CardType cardType, Integer energyCost, String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.trim().toUpperCase(Locale.ROOT);
        return getCatalogMap().values().stream()
                .filter(c -> faction == null || c.faction() == faction)
                .filter(c -> cardType == null || c.cardType() == cardType)
                .filter(c -> energyCost == null || c.energyCost() == energyCost)
                .filter(c -> normalizedKeyword == null
                        || normalizedKeyword.isEmpty()
                        || c.keywords().stream().anyMatch(k -> k.equalsIgnoreCase(normalizedKeyword)))
                .toList();
    }

    public List<CardDefinition> getStarterDeckCards(Faction faction) {
        return getCatalogMap().values().stream()
                .filter(c -> c.faction() == faction)
                .toList();
    }

    public Map<String, CardDefinition> resolveByIds(Collection<String> cardIds) {
        Map<String, CardDefinition> catalog = getCatalogMap();
        Map<String, CardDefinition> resolved = new LinkedHashMap<>();
        for (String cardId : cardIds) {
            CardDefinition def = catalog.get(cardId);
            if (def != null) {
                resolved.put(cardId, def);
            }
        }
        return resolved;
    }

    private Map<String, CardDefinition> getCatalogMap() {
        Map<String, CardDefinition> current = cache;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cache == null) {
                List<CardDefinition> defs = repository.findAllByOrderByCardIdAsc().stream()
                        .map(CardCatalogEntity::toDefinition)
                        .toList();
                cache = indexCards(defs);
            }
            return cache;
        }
    }

    private static Map<String, CardDefinition> indexCards(List<CardDefinition> cards) {
        Map<String, CardDefinition> map = new LinkedHashMap<>();
        for (CardDefinition card : cards) {
            map.put(card.cardId(), card);
        }
        return Collections.unmodifiableMap(map);
    }
}
