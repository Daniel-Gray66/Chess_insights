package com.chessinsights.repository;

import com.chessinsights.entity.SyncJob;
import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {
    List<SyncJob> findByUserOrderByStartedAtDesc(User user);
    boolean existsByUserAndStatus(User user, String status);
}
