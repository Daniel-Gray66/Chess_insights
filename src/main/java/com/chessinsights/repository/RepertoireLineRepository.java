package com.chessinsights.repository;

import com.chessinsights.entity.RepertoireLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepertoireLineRepository extends JpaRepository<RepertoireLine, UUID> {

    List<RepertoireLine> findByRepertoireId(UUID repertoireId);

    Optional<RepertoireLine> findByIdAndRepertoireId(UUID id, UUID repertoireId);

    /**
     * Find lines eligible for drilling, ordered by priority (highest first),
     * then by least recently drilled. Lines never drilled come first.
     */
    @Query("""
        SELECT rl FROM RepertoireLine rl
        WHERE rl.repertoire.id = :repertoireId
        ORDER BY rl.drillPriority DESC,
                 rl.lastDrilledAt ASC NULLS FIRST
        """)
    List<RepertoireLine> findDrillCandidates(@Param("repertoireId") UUID repertoireId);
}