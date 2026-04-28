package com.chessinsights.repository;

import com.chessinsights.entity.ChessGame;
import com.chessinsights.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<ChessGame, Long> {

    Optional<ChessGame> findByUserAndChessComGameId(User user, String chessComGameId);

    boolean existsByUserAndChessComGameId(User user, String chessComGameId);

    List<ChessGame> findByUserOrderByPlayedAtDesc(User user);

    List<ChessGame> findByUserAndTimeClassOrderByPlayedAtDesc(User user, String timeClass);

    // --- Stats Queries ---

    @Query("SELECT g.result, COUNT(g) FROM ChessGame g WHERE g.user = :user GROUP BY g.result")
    List<Object[]> countByResult(@Param("user") User user);

    @Query("SELECT g.result, COUNT(g) FROM ChessGame g WHERE g.user = :user AND g.timeClass = :timeClass GROUP BY g.result")
    List<Object[]> countByResultAndTimeClass(@Param("user") User user, @Param("timeClass") String timeClass);

    @Query("SELECT g.userColor, g.result, COUNT(g) FROM ChessGame g WHERE g.user = :user GROUP BY g.userColor, g.result")
    List<Object[]> countByColorAndResult(@Param("user") User user);

    // --- Opening Analysis ---

    @Query("SELECT g.openingName, g.ecoCode, g.result, COUNT(g) " +
           "FROM ChessGame g WHERE g.user = :user AND g.openingName IS NOT NULL " +
           "GROUP BY g.openingName, g.ecoCode, g.result " +
           "ORDER BY g.openingName")
    List<Object[]> countByOpeningAndResult(@Param("user") User user);

    // --- Rating Progression ---

    @Query("SELECT g.playedAt, g.userRating, g.timeClass FROM ChessGame g " +
           "WHERE g.user = :user ORDER BY g.playedAt ASC")
    List<Object[]> getRatingProgression(@Param("user") User user);

    @Query("SELECT g.playedAt, g.userRating FROM ChessGame g " +
           "WHERE g.user = :user AND g.timeClass = :timeClass ORDER BY g.playedAt ASC")
    List<Object[]> getRatingProgressionByTimeClass(@Param("user") User user, @Param("timeClass") String timeClass);

    // --- Time of Day / Day of Week Analysis ---
    // Note: These use native queries for Postgres EXTRACT function

    @Query(value = "SELECT EXTRACT(HOUR FROM played_at AT TIME ZONE 'UTC') as hour, " +
                   "result, COUNT(*) FROM chess_games " +
                   "WHERE user_id = :userId GROUP BY hour, result ORDER BY hour",
           nativeQuery = true)
    List<Object[]> countByHourOfDayAndResult(@Param("userId") Long userId);

    @Query(value = "SELECT EXTRACT(DOW FROM played_at AT TIME ZONE 'UTC') as dow, " +
                   "result, COUNT(*) FROM chess_games " +
                   "WHERE user_id = :userId GROUP BY dow, result ORDER BY dow",
           nativeQuery = true)
    List<Object[]> countByDayOfWeekAndResult(@Param("userId") Long userId);

    // --- Search / Filter ---

    @Query("SELECT g FROM ChessGame g WHERE g.user = :user " +
           "AND (:timeClass IS NULL OR g.timeClass = :timeClass) " +
           "AND (:result IS NULL OR g.result = :result) " +
           "AND (:opponent IS NULL OR LOWER(g.opponentUsername) LIKE LOWER(CONCAT('%', :opponent, '%'))) " +
           "AND (:opening IS NULL OR LOWER(g.openingName) LIKE LOWER(CONCAT('%', :opening, '%'))) " +
           "AND (:from IS NULL OR g.playedAt >= :from) " +
           "AND (:to IS NULL OR g.playedAt <= :to) " +
           "ORDER BY g.playedAt DESC")
    List<ChessGame> searchGames(
            @Param("user") User user,
            @Param("timeClass") String timeClass,
            @Param("result") String result,
            @Param("opponent") String opponent,
            @Param("opening") String opening,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COUNT(g) FROM ChessGame g WHERE g.user = :user")
    long countByUser(@Param("user") User user);
}
