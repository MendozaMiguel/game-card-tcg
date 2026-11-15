package com.ordredesarcane.online.game.dto;

import com.ordredesarcane.online.game.model.GameActionType;
import com.ordredesarcane.online.game.model.Posture;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record GameActionCommand(
        @NotNull(message = "matchId obligatoire") UUID matchId,
        UUID actingAsPlayerId,
        @NotNull(message = "actionType obligatoire") GameActionType actionType,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String cardInstanceId,
        @Min(0) @Max(3) Integer targetSlot,
        Posture posture,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String targetId,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String secondaryTargetId,
        @Min(0) @Max(4) Integer choiceIndex,
        @Size(max = 10) List<@NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String> instanceIds
) {
}
