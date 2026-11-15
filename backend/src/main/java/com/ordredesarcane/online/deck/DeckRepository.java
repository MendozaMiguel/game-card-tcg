package com.ordredesarcane.online.deck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckRepository extends JpaRepository<DeckEntity, UUID> {
    List<DeckEntity> findByUserIdOrderByFavoriteDescUpdatedAtDesc(UUID userId);
    Optional<DeckEntity> findByDeckIdAndUserId(UUID deckId, UUID userId);
    long countByUserId(UUID userId);
}
