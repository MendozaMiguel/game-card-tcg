package com.ordredesarcane.online.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cards_catalog")
public class CardCatalogEntity {

    @Id
    @Column(name = "card_id", length = 32, nullable = false)
    private String cardId;

    @Column(name = "name", length = 80, nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "faction", length = 16, nullable = false)
    private Faction faction;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 24, nullable = false)
    private CardType cardType;

    @Column(name = "is_epic", nullable = false)
    private boolean epic;

    @Column(name = "is_ramp", nullable = false)
    private boolean ramp;

    @Column(name = "energy_cost", nullable = false)
    private int energyCost;

    @Column(name = "base_atk")
    private Integer baseAtk;

    @Column(name = "base_def")
    private Integer baseDef;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> keywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "abilities_script", nullable = false, columnDefinition = "jsonb")
    private List<AbilityDefinition> abilitiesScript;

    protected CardCatalogEntity() {
    }

    public CardDefinition toDefinition() {
        return new CardDefinition(
                cardId,
                name,
                faction,
                cardType,
                epic,
                ramp,
                energyCost,
                baseAtk,
                baseDef,
                keywords == null ? List.of() : List.copyOf(keywords),
                description,
                abilitiesScript == null ? List.of() : List.copyOf(abilitiesScript));
    }

    public String getCardId() {
        return cardId;
    }

    public String getName() {
        return name;
    }

    public Faction getFaction() {
        return faction;
    }

    public CardType getCardType() {
        return cardType;
    }

    public boolean isEpic() {
        return epic;
    }

    public boolean isRamp() {
        return ramp;
    }

    public int getEnergyCost() {
        return energyCost;
    }
}
