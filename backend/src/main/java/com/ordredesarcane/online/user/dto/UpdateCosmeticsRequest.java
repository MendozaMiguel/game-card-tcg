package com.ordredesarcane.online.user.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateCosmeticsRequest(
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedCardBack,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedBoardSkin,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedPet,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedEnergySkin,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedAvatar,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedLeagueFrame,
        @Pattern(regexp = "^[A-Za-z0-9_\\- À-ÿ']{2,64}$") String equippedTitle,
        @Pattern(regexp = "^[A-Za-z0-9_\\- ]{2,64}$") String equippedFinisher
) {
}
