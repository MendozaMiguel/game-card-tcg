package com.ordredesarcane.online.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_cosmetics")
public class UserCosmeticEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cosmetic_type", length = 32, nullable = false)
    private CosmeticType cosmeticType;

    @Column(name = "item_id", length = 64, nullable = false)
    private String itemId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt = Instant.now();

    protected UserCosmeticEntity() {
    }

    public UserCosmeticEntity(UUID userId, CosmeticType cosmeticType, String itemId) {
        this.userId = userId;
        this.cosmeticType = cosmeticType;
        this.itemId = itemId;
        this.unlockedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public CosmeticType getCosmeticType() { return cosmeticType; }
    public String getItemId() { return itemId; }
    public Instant getUnlockedAt() { return unlockedAt; }
}
