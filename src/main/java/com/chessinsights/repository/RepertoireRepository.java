package com.chessinsights.repository;

import com.chessinsights.entity.Repertoire;
import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}