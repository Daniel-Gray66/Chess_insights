package com.chessinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chess_games", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "chess_com_game_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChessGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "chess_com_game_id", nullable = false)
    private String chessComGameId;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    // "rapid", "blitz", "bullet", "daily"
    @Column(name = "time_class", nullable = false)
    private String timeClass;

    // e.g. "600", "300+5", "180"
    @Column(name = "time_control")
    private String timeControl;

    // "white" or "black"
    @Column(name = "user_color", nullable = false)
    private String userColor;

    @Column(name = "user_rating", nullable = false)
    private Integer userRating;

    @Column(name = "opponent_username", nullable = false)
    private String opponentUsername;

    @Column(name = "opponent_rating", nullable = false)
    private Integer opponentRating;

    // "win", "loss", "draw"
    @Column(nullable = false)
    private String result;

    // e.g. "resign", "checkmate", "timeout", "stalemate", "insufficient", "repetition"
    @Column(name = "termination")
    private String termination;

    // ECO code like "B20", "C50"
    @Column(name = "eco_code")
    private String ecoCode;

    // Human-readable name like "Sicilian Defense"
    @Column(name = "opening_name")
    private String openingName;

    // Full PGN text of the game
    @Column(columnDefinition = "TEXT")
    private String pgn;

    @Column(name = "num_moves")
    private Integer numMoves;

    // Accuracy score from Chess.com game review (if available)
    @Column(name = "accuracy")
    private Double accuracy;

    // Store raw JSON from Chess.com for future parsing
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "game_url")
    private String gameUrl;
}
