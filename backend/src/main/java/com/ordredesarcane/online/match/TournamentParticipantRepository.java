package com.ordredesarcane.online.match;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipantEntity, TournamentParticipantId> {
    List<TournamentParticipantEntity> findByIdTournamentIdOrderBySeedAsc(UUID tournamentId);
    long countByIdTournamentId(UUID tournamentId);
}
