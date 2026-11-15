package com.ordredesarcane.online.match;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<TournamentEntity, UUID> {
    List<TournamentEntity> findAllByOrderByCreatedAtDesc();
}
