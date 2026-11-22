package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.catalog.Faction;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.TeamId;
import java.util.List;
import java.util.UUID;

public record PlayerBoardViewDto(
        UUID userId,
        String username,
        boolean bot,
        TeamId teamId,
        int seatIndex,
        boolean connected,
        UUID controlledByUserId,
        boolean mulliganCompleted,
        List<Integer> mulliganReplacedSlots,
        int energyTokens,
        int deckCount,
        int handCount,
        List<CardViewDto> hand,
        List<CardViewDto> monsters,
        List<CardViewDto> familiars,
        List<CardViewDto> spellArtifacts,
        List<CardViewDto> discard,
        CardViewDto peekedOpponentTopCard,
        boolean spellsLockedThisTurn,
        boolean directAttacksBlockedThisTurn,
        String cardBackSkin,
        String boardSkin,
        String pet,
        String energySkin,
        String avatar,
        String leagueFrame,
        String title,
        String finisher,
        AiDifficulty aiDifficulty,
        Faction primaryFaction
) {
}
