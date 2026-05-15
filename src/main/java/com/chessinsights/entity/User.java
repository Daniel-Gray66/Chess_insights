package com.chessinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column
    private String password;

    @Column(name = "chess_com_username", unique = true)
    private String chessComUsername;

    @Column(name = "supabase_id", unique = true)
    private String supabaseId;

    @Column
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}