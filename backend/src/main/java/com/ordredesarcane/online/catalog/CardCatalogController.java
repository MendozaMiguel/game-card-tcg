package com.ordredesarcane.online.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardCatalogController {

    private final CardCatalogService cardCatalogService;

    public CardCatalogController(CardCatalogService cardCatalogService) {
        this.cardCatalogService = cardCatalogService;
    }

    @GetMapping
    public List<CardDefinition> listCards(
            @RequestParam(required = false) Faction faction,
            @RequestParam(required = false) CardType type,
            @RequestParam(required = false) Integer cost,
            @RequestParam(required = false) String keyword) {
        return cardCatalogService.filter(faction, type, cost, keyword);
    }
}
