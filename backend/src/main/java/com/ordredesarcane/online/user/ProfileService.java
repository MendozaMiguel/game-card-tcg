package com.ordredesarcane.online.user;

import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.user.dto.CosmeticItemDto;
import com.ordredesarcane.online.user.dto.UpdateCosmeticsRequest;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    public static final List<String> STARTER_AVATAR_IDS = List.of(
            "avatar_initiate",
            "avatar_pyromancer",
            "avatar_cryomancer",
            "avatar_shadowmage",
            "avatar_luminarch"
    );
    private static final Set<String> STARTER_AVATAR_SET = Set.copyOf(STARTER_AVATAR_IDS);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserCosmeticRepository cosmeticRepository;

    public ProfileService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserCosmeticRepository cosmeticRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.cosmeticRepository = cosmeticRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Joueur introuvable."));
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil joueur introuvable."));
        List<CosmeticItemDto> cosmetics = new ArrayList<>();
        Set<String> presentMageProfileIds = new LinkedHashSet<>();
        for (UserCosmeticEntity c : cosmeticRepository.findByUserId(userId)) {
            if (c.getCosmeticType() == CosmeticType.MAGE_PROFILE) {
                presentMageProfileIds.add(c.getItemId());
            }
            cosmetics.add(new CosmeticItemDto(c.getCosmeticType(), c.getItemId(), c.getUnlockedAt()));
        }
        // Expose starter mage avatars to existing accounts without requiring a SQL data backfill.
        for (String starterAvatarId : STARTER_AVATAR_IDS) {
            if (!presentMageProfileIds.contains(starterAvatarId)) {
                cosmetics.add(new CosmeticItemDto(CosmeticType.MAGE_PROFILE, starterAvatarId, Instant.EPOCH));
            }
        }
        return toDto(user, profile, cosmetics);
    }

    @Transactional
    public UserProfileDto equipCosmetics(UUID userId, UpdateCosmeticsRequest req) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil joueur introuvable."));

        if (req.equippedCardBack() != null) {
            verifyOwned(userId, CosmeticType.CARD_BACK, req.equippedCardBack());
            profile.setEquippedCardBack(req.equippedCardBack());
        }
        if (req.equippedBoardSkin() != null) {
            verifyOwned(userId, CosmeticType.BOARD_SKIN, req.equippedBoardSkin());
            profile.setEquippedBoardSkin(req.equippedBoardSkin());
        }
        if (req.equippedPet() != null) {
            verifyOwned(userId, CosmeticType.COMPANION_PET, req.equippedPet());
            profile.setEquippedPet(req.equippedPet());
        }
        if (req.equippedEnergySkin() != null) {
            verifyOwned(userId, CosmeticType.ENERGY_SKIN, req.equippedEnergySkin());
            profile.setEquippedEnergySkin(req.equippedEnergySkin());
        }
        if (req.equippedAvatar() != null) {
            verifyOwned(userId, CosmeticType.MAGE_PROFILE, req.equippedAvatar());
            profile.setEquippedAvatar(req.equippedAvatar());
        }
        if (req.equippedLeagueFrame() != null) {
            verifyOwned(userId, CosmeticType.MAGE_PROFILE, req.equippedLeagueFrame());
            profile.setEquippedLeagueFrame(req.equippedLeagueFrame());
        }
        if (req.equippedTitle() != null) {
            verifyOwned(userId, CosmeticType.MAGE_PROFILE, req.equippedTitle());
            profile.setEquippedTitle(req.equippedTitle());
        }
        if (req.equippedFinisher() != null) {
            verifyOwned(userId, CosmeticType.EMOTE_FINISHER, req.equippedFinisher());
            profile.setEquippedFinisher(req.equippedFinisher());
        }

        profileRepository.save(profile);
        return getProfile(userId);
    }

    /** Marks the interactive new-player tutorial as completed on the user's profile. */
    @Transactional
    public UserProfileDto markTutorialCompleted(UUID userId) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil joueur introuvable."));
        if (!profile.isTutorialCompleted()) {
            profile.setTutorialCompleted(true);
            profileRepository.save(profile);
        }
        return getProfile(userId);
    }

    private void verifyOwned(UUID userId, CosmeticType type, String itemId) {
        if (type == CosmeticType.MAGE_PROFILE && STARTER_AVATAR_SET.contains(itemId)) {
            return;
        }
        if (!cosmeticRepository.existsByUserIdAndCosmeticTypeAndItemId(userId, type, itemId)) {
            throw new IllegalArgumentException("Cosmétique non débloqué : " + itemId);
        }
    }

    public static UserProfileDto toDto(
            UserEntity user,
            UserProfileEntity profile,
            List<CosmeticItemDto> cosmetics) {
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                profile.getMmr1v1(),
                profile.getMmr2v2(),
                profile.getLeagueTier(),
                profile.getGoldenShards(),
                profile.getArcaneCrowns(),
                profile.getEquippedCardBack(),
                profile.getEquippedBoardSkin(),
                profile.getEquippedPet(),
                profile.getEquippedEnergySkin(),
                profile.getEquippedAvatar(),
                profile.getEquippedLeagueFrame(),
                profile.getEquippedTitle(),
                profile.getEquippedFinisher(),
                profile.getFactionWins() == null ? Map.of() : Map.copyOf(profile.getFactionWins()),
                cosmetics,
                profile.isTutorialCompleted());
    }
}
