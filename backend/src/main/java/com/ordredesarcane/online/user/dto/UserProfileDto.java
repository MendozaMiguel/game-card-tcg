package com.ordredesarcane.online.user.dto;

import com.ordredesarcane.online.user.LeagueTier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserProfileDto(
        UUID userId,
        String username,
        int mmr1v1,
        int mmr2v2,
        LeagueTier leagueTier,
        int goldenShards,
        int arcaneCrowns,
        String equippedCardBack,
        String equippedBoardSkin,
        String equippedPet,
        String equippedEnergySkin,
        String equippedAvatar,
        String equippedLeagueFrame,
        String equippedTitle,
        String equippedFinisher,
        Map<String, Integer> factionWins,
        List<CosmeticItemDto> unlockedCosmetics,
        boolean tutorialCompleted
) {
}
