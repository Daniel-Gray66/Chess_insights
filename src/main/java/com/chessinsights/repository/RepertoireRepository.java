package com.chessinsights.repository;

import com.chessinsights.entity.Repertoire;
import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepertoireRepository extends JpaRepository<Repertoire, UUID> {

    List<Repertoire> findByPlayer(User player);

    List<Repertoire> findByPlayerAndColor(User player, Repertoire.Color color);

    Optional<Repertoire> findByIdAndPlayer(UUID id, User player);

    List<Repertoire> findByPlayerAndRootMove(User player, String rootMove);

    // ── Community queries ────────────────────────────────────

    @Query("SELECT r FROM Repertoire r JOIN FETCH r.player WHERE r.visibility = 'PUBLIC' ORDER BY r.updatedAt DESC")
    List<Repertoire> findPublicRepertoires();

    @Query("SELECT r FROM Repertoire r JOIN FETCH r.player WHERE r.visibility = 'PUBLIC' AND r.color = :color ORDER BY r.updatedAt DESC")
    List<Repertoire> findPublicRepertoiresByColor(@Param("color") Repertoire.Color color);

    @Query("SELECT r FROM Repertoire r JOIN FETCH r.player WHERE r.visibility = 'PUBLIC' AND " +
           "(LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY r.updatedAt DESC")
    List<Repertoire> searchPublicRepertoires(@Param("query") String query);

    @Query("SELECT r FROM Repertoire r JOIN FETCH r.player WHERE r.visibility = 'PUBLIC' AND " +
           "(LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "r.color = :color ORDER BY r.updatedAt DESC")
    List<Repertoire> searchPublicRepertoiresByColor(@Param("query") String query, @Param("color") Repertoire.Color color);
}