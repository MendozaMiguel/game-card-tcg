package com.ordredesarcane.online.user;

import com.ordredesarcane.online.match.MatchSource;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.user.dto.PlayerMatchHistoryDtos.PlayerMatchHistoryResponse;
import com.ordredesarcane.online.user.dto.UpdateCosmeticsRequest;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final PlayerMatchHistoryService matchHistoryService;

    public ProfileController(ProfileService profileService, PlayerMatchHistoryService matchHistoryService) {
        this.profileService = profileService;
        this.matchHistoryService = matchHistoryService;
    }

    @GetMapping
    public UserProfileDto getMyProfile(@AuthenticationPrincipal PlayerPrincipal principal) {
        return profileService.getProfile(principal.userId());
    }

    @GetMapping("/matches")
    public PlayerMatchHistoryResponse getMyMatchHistory(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) MatchSource source) {
        return matchHistoryService.getPlayerHistory(principal.userId(), limit, source);
    }

    @PutMapping("/cosmetics")
    public UserProfileDto equipCosmetics(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody UpdateCosmeticsRequest request) {
        return profileService.equipCosmetics(principal.userId(), request);
    }

    @PostMapping("/tutorial/complete")
    public UserProfileDto completeTutorial(@AuthenticationPrincipal PlayerPrincipal principal) {
        return profileService.markTutorialCompleted(principal.userId());
    }
}
