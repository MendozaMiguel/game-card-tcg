package com.ordredesarcane.online.social;

import com.ordredesarcane.online.security.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping
    public List<FriendDto> listFriends(@AuthenticationPrincipal PlayerPrincipal principal) {
        return friendshipService.listFriendships(principal.userId());
    }

    @PostMapping("/request")
    public ResponseEntity<FriendDto> sendRequest(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody FriendRequestInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(friendshipService.sendRequest(principal.userId(), input.username()));
    }

    @PostMapping("/{friendshipId}/accept")
    public FriendDto acceptRequest(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID friendshipId) {
        return friendshipService.acceptRequest(principal.userId(), friendshipId);
    }

    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> removeFriendship(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID friendshipId) {
        friendshipService.removeFriendship(principal.userId(), friendshipId);
        return ResponseEntity.noContent().build();
    }
}
