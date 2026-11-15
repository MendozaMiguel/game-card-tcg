package com.ordredesarcane.online.user;

import com.ordredesarcane.online.api.ApiConflictException;
import com.ordredesarcane.online.deck.DeckService;
import com.ordredesarcane.online.security.JwtService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.user.dto.LoginRequest;
import com.ordredesarcane.online.user.dto.RegisterRequest;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record AuthResult(UserProfileDto profile, String jwtToken) {
    }

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserCosmeticRepository cosmeticRepository;
    private final DeckService deckService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileService profileService;

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserCosmeticRepository cosmeticRepository,
            DeckService deckService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ProfileService profileService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.cosmeticRepository = cosmeticRepository;
        this.deckService = deckService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.profileService = profileService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiConflictException("Ce pseudo est déjà utilisé.");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiConflictException("Cette adresse e-mail est déjà enregistrée.");
        }

        UserEntity user = userRepository.save(new UserEntity(
                username, normalizedEmail, passwordEncoder.encode(request.password())));
        profileRepository.save(new UserProfileEntity(user.getId()));
        grantStarterCosmetics(user.getId());
        deckService.createStarterDecksForUser(user.getId());

        String token = jwtService.issueToken(new PlayerPrincipal(user.getId(), user.getUsername(), user.getRole()));
        UserProfileDto profile = profileService.getProfile(user.getId());
        return new AuthResult(profile, token);
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request) {
        String identifier = request.identifier().trim();
        UserEntity user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides.");
        }

        String token = jwtService.issueToken(new PlayerPrincipal(user.getId(), user.getUsername(), user.getRole()));
        UserProfileDto profile = profileService.getProfile(user.getId());
        return new AuthResult(profile, token);
    }

    private void grantStarterCosmetics(UUID userId) {
        List<UserCosmeticEntity> starters = List.of(
                new UserCosmeticEntity(userId, CosmeticType.CARD_BACK, "card_back_default"),
                new UserCosmeticEntity(userId, CosmeticType.BOARD_SKIN, "board_sanctuary"),
                new UserCosmeticEntity(userId, CosmeticType.COMPANION_PET, "pet_none"),
                new UserCosmeticEntity(userId, CosmeticType.ENERGY_SKIN, "energy_arcane"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "avatar_initiate"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "avatar_pyromancer"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "avatar_cryomancer"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "avatar_shadowmage"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "avatar_luminarch"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "frame_apprenti"),
                new UserCosmeticEntity(userId, CosmeticType.MAGE_PROFILE, "Apprenti Arcane"),
                new UserCosmeticEntity(userId, CosmeticType.EMOTE_FINISHER, "finisher_arcane_burst")
        );
        cosmeticRepository.saveAll(starters);
    }
}
