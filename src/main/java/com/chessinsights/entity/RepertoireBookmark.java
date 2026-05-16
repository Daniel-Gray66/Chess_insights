package com.chessinsights.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repertoire_bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repertoire_id"}))
public class RepertoireBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repertoire_id", nullable = false)
    private Repertoire repertoire;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public RepertoireBookmark() {}

    public RepertoireBookmark(User user, Repertoire repertoire) {
        this.user = user;
        this.repertoire = repertoire;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Repertoire getRepertoire() { return repertoire; }
    public Instant getCreatedAt() { return createdAt; }
}