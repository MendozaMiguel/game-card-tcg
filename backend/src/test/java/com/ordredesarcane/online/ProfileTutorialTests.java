package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.user.PlayerMatchHistoryService;
import com.ordredesarcane.online.user.ProfileController;
import com.ordredesarcane.online.user.ProfileService;
import com.ordredesarcane.online.user.UserCosmeticRepository;
import com.ordredesarcane.online.user.UserEntity;
import com.ordredesarcane.online.user.UserProfileEntity;
import com.ordredesarcane.online.user.UserProfileRepository;
import com.ordredesarcane.online.user.UserRepository;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileTutorialTests {

    @Test
    void newProfileStartsWithTutorialPendingAndCompletionPersistsIdempotently() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity("NoviceMage", "novice@arcane.gg", "hash");
        ReflectionTestUtils.setField(user, "id", userId);
        UserProfileEntity profile = new UserProfileEntity(userId);

        assertThat(profile.isTutorialCompleted()).isFalse();

        UserRepository userRepo = mock(UserRepository.class);
        UserProfileRepository profileRepo = mock(UserProfileRepository.class);
        UserCosmeticRepository cosmeticRepo = mock(UserCosmeticRepository.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepo.findById(userId)).thenReturn(Optional.of(profile));
        when(cosmeticRepo.findByUserId(userId)).thenReturn(List.of());

        ProfileService service = new ProfileService(userRepo, profileRepo, cosmeticRepo);

        UserProfileDto initialDto = service.getProfile(userId);
        assertThat(initialDto.tutorialCompleted()).isFalse();

        UserProfileDto completedDto = service.markTutorialCompleted(userId);
        assertThat(completedDto.tutorialCompleted()).isTrue();
        assertThat(profile.isTutorialCompleted()).isTrue();
        verify(profileRepo, times(1)).save(profile);

        UserProfileDto idempotentDto = service.markTutorialCompleted(userId);
        assertThat(idempotentDto.tutorialCompleted()).isTrue();
        verify(profileRepo, times(1)).save(any());
    }

    @Test
    void profileControllerDelegatesCompleteTutorialToService() {
        UUID userId = UUID.randomUUID();
        ProfileService service = mock(ProfileService.class);
        PlayerMatchHistoryService historyService = mock(PlayerMatchHistoryService.class);
        ProfileController controller = new ProfileController(service, historyService);

        UserProfileDto expected = mock(UserProfileDto.class);
        when(service.markTutorialCompleted(userId)).thenReturn(expected);

        UserProfileDto actual = controller.completeTutorial(new PlayerPrincipal(userId, "NoviceMage", "USER"));
        assertThat(actual).isSameAs(expected);
        verify(service).markTutorialCompleted(userId);
        verify(historyService, never()).getPlayerHistory(any(), any(Integer.class), any());
    }
}
