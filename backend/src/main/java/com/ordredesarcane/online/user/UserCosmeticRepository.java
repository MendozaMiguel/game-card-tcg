package com.ordredesarcane.online.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCosmeticRepository extends JpaRepository<UserCosmeticEntity, UUID> {
    List<UserCosmeticEntity> findByUserId(UUID userId);
    boolean existsByUserIdAndCosmeticTypeAndItemId(UUID userId, CosmeticType cosmeticType, String itemId);
}
