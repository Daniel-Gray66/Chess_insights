package com.chessinsights.repository;

import com.chessinsights.entity.LineMove;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LineMoveRepository extends JpaRepository<LineMove, UUID> {
}