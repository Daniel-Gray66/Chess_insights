package com.chessinsights.repository;

import com.chessinsights.entity.Opening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpeningRepository extends JpaRepository<Opening, UUID> {

    Optional<Opening> findByEcoCodeAndVariation(String ecoCode, String variation);

    List<Opening> findByNameContainingIgnoreCase(String name);

    List<Opening> findByEcoCode(String ecoCode);
}