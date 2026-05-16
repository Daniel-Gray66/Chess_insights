package com.chessinsights.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repertoires")
public class Repertoire {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Color color;

    @Column(name = "root_move", length = 10)
    private String rootMove;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PRIVATE;

    @OneToMany(mappedBy = "repertoire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<RepertoireLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Color {
        WHITE, BLACK
    }

    public enum Visibility {
        PRIVATE, PUBLIC, SHARED
    }

    public Repertoire() {}

    public Repertoire(User player, String name, Color color, String rootMove, String description) {
        this.player = player;
        this.name = name;
        this.color = color;
        this.rootMove = rootMove;
        this.description = description;
        this.visibility = Visibility.PRIVATE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getPlayer() { return player; }
    public void setPlayer(User player) { this.player = player; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    public String getRootMove() { return rootMove; }
    public void setRootMove(String rootMove) { this.rootMove = rootMove; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public List<RepertoireLine> getLines() { return lines; }
    public void setLines(List<RepertoireLine> lines) { this.lines = lines; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}