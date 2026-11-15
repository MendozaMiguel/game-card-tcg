package com.ordredesarcane.online.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {
    List<FriendshipEntity> findByRequesterIdOrAddresseeId(UUID requesterId, UUID addresseeId);
}
