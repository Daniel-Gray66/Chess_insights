package com.chessinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "games_fetched")
    private Integer gamesFetched;

    @Column(name = "new_games_saved")
    private Integer newGamesSaved;

    // "RUNNING", "COMPLETED", "FAILED"
    @Column(nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "archives_processed")
    private Integer archivesProcessed;
}
