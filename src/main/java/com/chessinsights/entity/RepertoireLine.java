package com.chessinsights.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repertoire_lines")
public class RepertoireLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repertoire_id", nullable = false)
    private Repertoire repertoire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opening_id")
    private Opening opening;

    @Column(name = "line_name", nullable = false)
    private String lineName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pgn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "drill_priority", nullable = false)
    private int drillPriority = 5;

    @Column(name = "times_drilled", nullable = false)
    private int timesDrilled = 0;

    @Column(name = "last_drilled_at")
    private Instant lastDrilledAt;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("moveNumber ASC")
    private List<LineMove> moves = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public RepertoireLine() {}

    public RepertoireLine(Repertoire repertoire, String lineName, String pgn, String notes) {
        this.repertoire = repertoire;
        this.lineName = lineName;
        this.pgn = pgn;
        this.notes = notes;
    }

    public void recordDrill() {
        this.timesDrilled++;
        this.lastDrilledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Repertoire getRepertoire() { return repertoire; }
    public void setRepertoire(Repertoire repertoire) { this.repertoire = repertoire; }

    public Opening getOpening() { return opening; }
    public void setOpening(Opening opening) { this.opening = opening; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public String getPgn() { return pgn; }
    public void setPgn(String pgn) { this.pgn = pgn; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getDrillPriority() { return drillPriority; }
    public void setDrillPriority(int drillPriority) { this.drillPriority = drillPriority; }

    public int getTimesDrilled() { return timesDrilled; }
    public void setTimesDrilled(int timesDrilled) { this.timesDrilled = timesDrilled; }

    public Instant getLastDrilledAt() { return lastDrilledAt; }
    public void setLastDrilledAt(Instant lastDrilledAt) { this.lastDrilledAt = lastDrilledAt; }

    public List<LineMove> getMoves() { return moves; }
    public void setMoves(List<LineMove> moves) { this.moves = moves; }

    public Instant getCreatedAt() { return createdAt; }
}