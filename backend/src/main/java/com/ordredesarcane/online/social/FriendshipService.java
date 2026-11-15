package com.ordredesarcane.online.social;

import com.ordredesarcane.online.api.ApiConflictException;
import com.ordredesarcane.online.api.ResourceNotFoundException;
import com.ordredesarcane.online.game.ws.SessionPresenceService;
import com.ordredesarcane.online.user.UserEntity;
import com.ordredesarcane.online.user.UserRepository;
import com.ordredesarcane.online.user.FriendshipEntity;
import com.ordredesarcane.online.user.FriendshipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SessionPresenceService presenceService;

    public FriendshipService(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            SessionPresenceService presenceService) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public List<FriendDto> listFriendships(UUID userId) {
        return friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId).stream()
                .map(f -> toDto(f, userId))
                .toList();
    }

    @Transactional
    public FriendDto sendRequest(UUID requesterId, String targetUsername) {
        UserEntity addressee = userRepository.findByUsernameIgnoreCase(targetUsername.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Joueur introuvable : " + targetUsername));
        if (addressee.getId().equals(requesterId)) {
            throw new IllegalArgumentException("Impossible de s'ajouter soi-même en ami.");
        }
        boolean exists = friendshipRepository.findByRequesterIdOrAddresseeId(requesterId, requesterId).stream()
                .anyMatch(f -> f.getRequesterId().equals(addressee.getId()) || f.getAddresseeId().equals(addressee.getId()));
        if (exists) {
            throw new ApiConflictException("Une relation d'amitié ou demande existe déjà avec ce joueur.");
        }
        FriendshipEntity saved = friendshipRepository.save(new FriendshipEntity(requesterId, addressee.getId(), "PENDING"));
        return toDto(saved, requesterId);
    }

    @Transactional
    public FriendDto acceptRequest(UUID userId, UUID friendshipId) {
        FriendshipEntity entity = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'ami introuvable."));
        if (!entity.getAddresseeId().equals(userId)) {
            throw new AccessDeniedException("Seul le destinataire peut accepter cette demande.");
        }
        entity.setStatus("ACCEPTED");
        return toDto(friendshipRepository.save(entity), userId);
    }

    @Transactional
    public void removeFriendship(UUID userId, UUID friendshipId) {
        FriendshipEntity entity = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Relation introuvable."));
        if (!entity.getRequesterId().equals(userId) && !entity.getAddresseeId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé.");
        }
        friendshipRepository.delete(entity);
    }

    private FriendDto toDto(FriendshipEntity entity, UUID currentUserId) {
        boolean incoming = entity.getAddresseeId().equals(currentUserId);
        UUID otherId = incoming ? entity.getRequesterId() : entity.getAddresseeId();
        String otherName = userRepository.findById(otherId).map(UserEntity::getUsername).orElse("Mage");
        boolean online = "ACCEPTED".equals(entity.getStatus()) && presenceService.isOnline(otherId);
        return new FriendDto(
                entity.getId(),
                otherId,
                otherName,
                entity.getStatus(),
                incoming,
                online,
                entity.getCreatedAt());
    }
}
