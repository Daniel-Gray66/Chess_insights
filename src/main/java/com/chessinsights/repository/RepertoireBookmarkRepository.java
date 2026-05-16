package com.chessinsights.repository;

import com.chessinsights.entity.RepertoireBookmark;
import com.chessinsights.entity.Repertoire;
import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepertoireBookmarkRepository extends JpaRepository<RepertoireBookmark, UUID> {

    List<RepertoireBookmark> findByUserOrderByCreatedAtDesc(User user);

    Optional<RepertoireBookmark> findByUserAndRepertoire(User user, Repertoire repertoire);

    boolean existsByUserAndRepertoire(User user, Repertoire repertoire);

    void deleteByUserAndRepertoire(User user, Repertoire repertoire);

    long countByRepertoire(Repertoire repertoire);
}