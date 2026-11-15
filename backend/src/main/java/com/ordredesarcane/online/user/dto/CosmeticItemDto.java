package com.ordredesarcane.online.user.dto;

import com.ordredesarcane.online.user.CosmeticType;
import java.time.Instant;

public record CosmeticItemDto(
        CosmeticType cosmeticType,
        String itemId,
        Instant unlockedAt
) {
}
