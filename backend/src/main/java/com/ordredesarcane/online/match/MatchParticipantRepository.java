package com.ordredesarcane.online.match;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity, UUID> {

    interface SourceModeAggregate {
        MatchSource getMatchSource();
        String getMode();
        long getTotalCount();
        long getWinCount();
        Long getTurnSum();
    }

    interface FactionAggregate {
        String getFaction();
        long getPlayCount();
        long getWinCount();
    }

    @Query("""
            SELECT p
            FROM MatchParticipantEntity p
            JOIN FETCH p.match m
            WHERE p.userId = :userId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<MatchParticipantEntity> findRecentWithMatchByUserId(
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query("""
            SELECT p
            FROM MatchParticipantEntity p
            JOIN FETCH p.match m
            WHERE p.userId = :userId AND m.matchSource = :source
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<MatchParticipantEntity> findRecentWithMatchByUserIdAndSource(
            @Param("userId") UUID userId,
            @Param("source") MatchSource source,
            Pageable pageable);

    @Query("""
            SELECT p
            FROM MatchParticipantEntity p
            WHERE p.match.matchId IN :matchIds
            ORDER BY p.match.matchId ASC, p.seatIndex ASC
            """)
    List<MatchParticipantEntity> findAllForMatchesOrdered(@Param("matchIds") Collection<UUID> matchIds);

    @Query("""
            SELECT m.matchSource AS matchSource,
                   m.mode AS mode,
                   COUNT(p) AS totalCount,
                   SUM(CASE WHEN p.won = true THEN 1L ELSE 0L END) AS winCount,
                   SUM(m.turnCount) AS turnSum
            FROM MatchParticipantEntity p
            JOIN p.match m
            WHERE p.userId = :userId
            GROUP BY m.matchSource, m.mode
            """)
    List<SourceModeAggregate> summarizeBySourceAndMode(@Param("userId") UUID userId);

    @Query("""
            SELECT p.primaryFaction AS faction,
                   COUNT(p) AS playCount,
                   SUM(CASE WHEN p.won = true THEN 1L ELSE 0L END) AS winCount
            FROM MatchParticipantEntity p
            WHERE p.userId = :userId AND p.primaryFaction IS NOT NULL
            GROUP BY p.primaryFaction
            ORDER BY COUNT(p) DESC, SUM(CASE WHEN p.won = true THEN 1L ELSE 0L END) DESC
            """)
    List<FactionAggregate> summarizeFactionsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT p.won
            FROM MatchParticipantEntity p
            WHERE p.userId = :userId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Boolean> findRecentOutcomesByUserId(@Param("userId") UUID userId, Pageable pageable);
}
