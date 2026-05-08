package com.chessinsights.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "line_moves")
public class LineMove {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private RepertoireLine line;

    @Column(name = "move_number", nullable = false)
    private int moveNumber;

    @Column(name = "move_uci", nullable = false, length = 5)
    private String moveUci;

    @Column(name = "move_san", nullable = false, length = 10)
    private String moveSan;

    @Column(name = "fen_after", nullable = false)
    private String fenAfter;

    @Column(columnDefinition = "TEXT")
    private String annotation;

    public LineMove() {}

    public LineMove(RepertoireLine line, int moveNumber, String moveUci, String moveSan, String fenAfter) {
        this.line = line;
        this.moveNumber = moveNumber;
        this.moveUci = moveUci;
        this.moveSan = moveSan;
        this.fenAfter = fenAfter;
    }

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public RepertoireLine getLine() { return line; }
    public void setLine(RepertoireLine line) { this.line = line; }

    public int getMoveNumber() { return moveNumber; }
    public void setMoveNumber(int moveNumber) { this.moveNumber = moveNumber; }

    public String getMoveUci() { return moveUci; }
    public void setMoveUci(String moveUci) { this.moveUci = moveUci; }

    public String getMoveSan() { return moveSan; }
    public void setMoveSan(String moveSan) { this.moveSan = moveSan; }

    public String getFenAfter() { return fenAfter; }
    public void setFenAfter(String fenAfter) { this.fenAfter = fenAfter; }

    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }
}
