package com.ordredesarcane.online.match;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {
    List<MatchEntity> findTop20ByOrderByCreatedAtDesc();
}
