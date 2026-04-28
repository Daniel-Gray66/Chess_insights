package com.chessinsights.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.chessinsights.dto.InsightsDto;
import com.chessinsights.entity.ChessGame;
import com.chessinsights.entity.User;
import com.chessinsights.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final GameRepository gameRepository;

   public InsightsDto generateInsights(User user, String timeClass, String period, String color) {
        List<ChessGame> games;
        if (timeClass != null) {
            games = gameRepository.findByUserAndTimeClassOrderByPlayedAtDesc(user, timeClass);
        } else {
            games = gameRepository.findByUserOrderByPlayedAtDesc(user);
        }

        // Apply time period filter
        Instant cutoff = parsePeriod(period);
        if (cutoff != null) {
            games = games.stream()
                    .filter(g -> g.getPlayedAt().isAfter(cutoff))
                    .collect(Collectors.toList());
        }

         if (color != null) {
            games = games.stream()
                    .filter(g -> color.equalsIgnoreCase(g.getUserColor()))
                    .collect(Collectors.toList());
        }

        if (games.isEmpty()) {
            return InsightsDto.builder().build();
        }

        // Reverse to chronological order for streak/tilt analysis
        List<ChessGame> chronological = new ArrayList<>(games);
        Collections.reverse(chronological);

        return InsightsDto.builder()
                .actualRating(chronological.get(chronological.size() - 1).getUserRating())
                .performanceRating(calculatePerformanceRating(chronological))
                .performanceVerdict(getPerformanceVerdict(
                        chronological.get(chronological.size() - 1).getUserRating(),
                        calculatePerformanceRating(chronological)))
                .longestWinStreak(calculateLongestStreak(chronological, "win"))
                .longestLossStreak(calculateLongestStreak(chronological, "loss"))
                .currentStreak(calculateCurrentStreak(chronological).get("count"))
                .currentStreakType(calculateCurrentStreakType(chronological))
                .tiltSessions(detectTiltSessions(chronological).size())
                .tiltSessionDetails(detectTiltSessions(chronological))
                .recommendedOpenings(getRecommendedOpenings(games))
                .avoidOpenings(getAvoidOpenings(games))
                .playStyle(analyzePlayStyle(games))
                .topRival(findTopRival(games))
                .avgOpponentRating(calculateAvgOpponentRatings(games).get("all"))
                .avgOpponentRatingWins(calculateAvgOpponentRatings(games).get("wins"))
                .avgOpponentRatingLosses(calculateAvgOpponentRatings(games).get("losses"))
                .avgOpponentRatingDraws(calculateAvgOpponentRatings(games).get("draws"))
                .avgPlayerRating((Double) calculatePlayerRatingStats(games).get("avg"))
                .highestRating((Integer) calculatePlayerRatingStats(games).get("highest"))
                .lowestRating((Integer) calculatePlayerRatingStats(games).get("lowest"))
                .build();
    }

    /**
     * Parse period string into a cutoff Instant.
     * Supported: 7d, 30d, 90d, 6m, 1y, all
     */
    private Instant parsePeriod(String period) {
        if (period == null || "all".equalsIgnoreCase(period)) {
            return null;
        }

        Instant now = Instant.now();
        return switch (period.toLowerCase()) {
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            case "90d" -> now.minus(Duration.ofDays(90));
            case "6m" -> now.minus(Duration.ofDays(180));
            case "1y" -> now.minus(Duration.ofDays(365));
            default -> null;
        };
    }


    /**
     * Calculate player's average, highest, and lowest rating for the period.
     */
    private Map<String, Number> calculatePlayerRatingStats(List<ChessGame> games) {
        Map<String, Number> stats = new LinkedHashMap<>();

        double avg = games.stream()
                .mapToInt(ChessGame::getUserRating)
                .average()
                .orElse(0);

        int highest = games.stream()
                .mapToInt(ChessGame::getUserRating)
                .max()
                .orElse(0);

        int lowest = games.stream()
                .mapToInt(ChessGame::getUserRating)
                .min()
                .orElse(0);

        stats.put("avg", Math.round(avg * 10.0) / 10.0);
        stats.put("highest", highest);
        stats.put("lowest", lowest);

        return stats;
    }


    /**
     * Calculate average opponent ratings overall and by result.
     */
    private Map<String, Double> calculateAvgOpponentRatings(List<ChessGame> games) {
        Map<String, Double> ratings = new LinkedHashMap<>();

        double avgAll = games.stream()
                .mapToInt(ChessGame::getOpponentRating)
                .average()
                .orElse(0);

        double avgWins = games.stream()
                .filter(g -> "win".equals(g.getResult()))
                .mapToInt(ChessGame::getOpponentRating)
                .average()
                .orElse(0);

        double avgLosses = games.stream()
                .filter(g -> "loss".equals(g.getResult()))
                .mapToInt(ChessGame::getOpponentRating)
                .average()
                .orElse(0);

        double avgDraws = games.stream()
                .filter(g -> "draw".equals(g.getResult()))
                .mapToInt(ChessGame::getOpponentRating)
                .average()
                .orElse(0);

        ratings.put("all", Math.round(avgAll * 10.0) / 10.0);
        ratings.put("wins", Math.round(avgWins * 10.0) / 10.0);
        ratings.put("losses", Math.round(avgLosses * 10.0) / 10.0);
        ratings.put("draws", Math.round(avgDraws * 10.0) / 10.0);

        return ratings;
    }

    /**
     * Performance Rating using simplified Elo expected score formula.
     *
     * For each game, calculate the expected score based on rating difference,
     * then compare actual results to expected. Performance rating is the rating
     * at which your expected score would equal your actual score.
     *
     * Formula: E = 1 / (1 + 10^((opponentRating - playerRating) / 400))
     * Performance = avgOpponentRating + 400 * log10(wins / losses)
     */
    private Double calculatePerformanceRating(List<ChessGame> games) {
        // Use last 50 games for recent performance
        List<ChessGame> recentGames = games.subList(
                Math.max(0, games.size() - 50), games.size());

        double totalOpponentRating = 0;
        int wins = 0;
        int losses = 0;

        for (ChessGame game : recentGames) {
            totalOpponentRating += game.getOpponentRating();
            if ("win".equals(game.getResult())) wins++;
            else if ("loss".equals(game.getResult())) losses++;
        }

        if (wins == 0 && losses == 0) return null;

        double avgOpponentRating = totalOpponentRating / recentGames.size();

        // Avoid division by zero — if no losses, cap at a high performance
        if (losses == 0) return avgOpponentRating + 400.0;
        if (wins == 0) return avgOpponentRating - 400.0;

        return Math.round((avgOpponentRating + 400.0 * Math.log10((double) wins / losses)) * 10.0) / 10.0;
    }

    private String getPerformanceVerdict(Integer actualRating, Double performanceRating) {
        if (performanceRating == null || actualRating == null) return "Not enough data";

        double diff = performanceRating - actualRating;
        if (diff > 100) return "Playing significantly above your rating";
        if (diff > 50) return "Playing above your rating";
        if (diff > -50) return "Playing at your level";
        if (diff > -100) return "Playing below your rating";
        return "Playing significantly below your rating — consider taking a break";
    }

    /**
     * Find the longest consecutive streak of a given result.
     */
    private Integer calculateLongestStreak(List<ChessGame> games, String targetResult) {
        int longest = 0;
        int current = 0;

        for (ChessGame game : games) {
            if (targetResult.equals(game.getResult())) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private Map<String, Integer> calculateCurrentStreak(List<ChessGame> games) {
        if (games.isEmpty()) return Map.of("count", 0);

        String lastResult = games.get(games.size() - 1).getResult();
        int count = 0;

        for (int i = games.size() - 1; i >= 0; i--) {
            if (games.get(i).getResult().equals(lastResult)) {
                count++;
            } else {
                break;
            }
        }
        return Map.of("count", count);
    }

    private String calculateCurrentStreakType(List<ChessGame> games) {
        if (games.isEmpty()) return "none";
        return games.get(games.size() - 1).getResult();
    }

    /**
     * Tilt Detection: identifies sessions where a player lost 3+ games in a row
     * within a short time window (under 2 hours) and kept playing.
     *
     * A tilt session is defined as:
     * - 3+ consecutive losses
     * - Games played within 2 hours of each other
     * - Total rating drop tracked
     */
    private List<InsightsDto.TiltSession> detectTiltSessions(List<ChessGame> games) {
        List<InsightsDto.TiltSession> tiltSessions = new ArrayList<>();
        int i = 0;

        while (i < games.size()) {
            // Look for start of a losing streak
            if ("loss".equals(games.get(i).getResult())) {
                int streakStart = i;
                int consecutiveLosses = 0;
                Instant sessionStart = games.get(i).getPlayedAt();
                Instant lastGameTime = sessionStart;

                while (i < games.size() && "loss".equals(games.get(i).getResult())) {
                    Instant currentTime = games.get(i).getPlayedAt();

                    // Check if this game is within 2 hours of the last one
                    if (consecutiveLosses > 0 &&
                            Duration.between(lastGameTime, currentTime).toHours() > 2) {
                        break;
                    }

                    consecutiveLosses++;
                    lastGameTime = currentTime;
                    i++;
                }

                // Only count as tilt if 4+ losses in a session
                if (consecutiveLosses >= 4) {
                    int ratingDrop = games.get(streakStart).getUserRating() -
                            games.get(Math.min(i - 1, games.size() - 1)).getUserRating();

                    tiltSessions.add(InsightsDto.TiltSession.builder()
                            .startedAt(sessionStart)
                            .endedAt(lastGameTime)
                            .gamesPlayed(consecutiveLosses)
                            .losses(consecutiveLosses)
                            .ratingDrop(Math.max(0, ratingDrop))
                            .build());
                }
            } else {
                i++;
            }
        }
        return tiltSessions;
    }


    /**
     * Group games by base opening name (first 2-3 words).
     * "Italian Game Two Knights Defense 4.Qe2" -> "Italian Game"
     * "Caro Kann Defense Main Line 4...Nf6" -> "Caro Kann Defense"
     * "Queens Pawn Opening Zukertort Variation" -> "Queens Pawn Opening"
     */
    private Map<String, List<ChessGame>> groupByBaseOpening(List<ChessGame> games) {
        return games.stream()
                .filter(g -> g.getOpeningName() != null && !g.getOpeningName().isEmpty())
                .collect(Collectors.groupingBy(g -> extractBaseOpening(g.getOpeningName())));
    }

    private String extractBaseOpening(String fullName) {
        // Common opening name patterns - extract the core name
        String[] knownBases = {
            "Italian Game", "Sicilian Defense", "French Defense", "Caro Kann Defense",
            "Queens Gambit", "Kings Gambit", "Ruy Lopez", "English Opening",
            "Dutch Defense", "Kings Indian Defense", "Queens Indian Defense",
            "Nimzo Indian Defense", "Grunfeld Defense", "Scandinavian Defense",
            "Pirc Defense", "Alekhine Defense", "Modern Defense", "Vienna Game",
            "Scotch Game", "Petrov Defense", "Philidor Defense", "London System",
            "Kings Pawn Opening", "Queens Pawn Opening", "Reti Opening",
            "Catalan Opening", "Benoni Defense", "Slav Defense",
            "Bird Opening", "Giuoco Piano", "Four Knights Game",
            "Three Knights Opening", "Two Knights Defense",
            "Nimzowitsch Defense", "Nimzowitsch Larsen Attack",
            "Center Game", "Danish Gambit", "Evans Gambit",
            "Hungarian Opening", "Polish Opening"
        };

        for (String base : knownBases) {
            if (fullName.startsWith(base)) {
                return base;
            }
        }

        // Fallback: take first 2-3 words
        String[] words = fullName.split(" ");
        int wordCount = Math.min(words.length, 3);
        // If 3rd word is a move notation (contains digits or dots), use only 2 words
        if (wordCount == 3 && words[2].matches(".*\\d.*")) {
            wordCount = 2;
        }
        return String.join(" ", java.util.Arrays.copyOf(words, wordCount));
    }

    /**
     * Recommend openings with high win rates and sufficient sample size.
     * Minimum 5 games to count, sorted by win rate.
     */
    private List<InsightsDto.OpeningRecommendation> getRecommendedOpenings(List<ChessGame> games) {
        Map<String, List<ChessGame>> byOpening = groupByBaseOpening(games);

        return byOpening.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .map(e -> {
                    List<ChessGame> openingGames = e.getValue();
                    long wins = openingGames.stream().filter(g -> "win".equals(g.getResult())).count();
                    double winRate = (double) wins / openingGames.size() * 100;

                    // Get most common ECO code in this group
                    String eco = openingGames.stream()
                            .filter(g -> g.getEcoCode() != null)
                            .collect(Collectors.groupingBy(ChessGame::getEcoCode, Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("N/A");

                    String confidence = openingGames.size() >= 15 ? "high" :
                            openingGames.size() >= 8 ? "medium" : "low";

                    return InsightsDto.OpeningRecommendation.builder()
                            .openingName(e.getKey())
                            .ecoCode(eco)
                            .gamesPlayed(openingGames.size())
                            .winRate(Math.round(winRate * 10.0) / 10.0)
                            .reason(String.format("%.0f%% win rate over %d games", winRate, openingGames.size()))
                            .confidence(confidence)
                            .build();
                })
                .filter(r -> r.getWinRate() >= 55.0)
                .sorted((a, b) -> Double.compare(b.getWinRate(), a.getWinRate()))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Identify openings to avoid — low win rate with enough games to be meaningful.
     */
 private List<InsightsDto.OpeningRecommendation> getAvoidOpenings(List<ChessGame> games) {
        Map<String, List<ChessGame>> byOpening = groupByBaseOpening(games);

        return byOpening.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .map(e -> {
                    List<ChessGame> openingGames = e.getValue();
                    long wins = openingGames.stream().filter(g -> "win".equals(g.getResult())).count();
                    double winRate = (double) wins / openingGames.size() * 100;

                    String eco = openingGames.stream()
                            .filter(g -> g.getEcoCode() != null)
                            .collect(Collectors.groupingBy(ChessGame::getEcoCode, Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("N/A");

                    String confidence = openingGames.size() >= 15 ? "high" :
                            openingGames.size() >= 8 ? "medium" : "low";

                    return InsightsDto.OpeningRecommendation.builder()
                            .openingName(e.getKey())
                            .ecoCode(eco)
                            .gamesPlayed(openingGames.size())
                            .winRate(Math.round(winRate * 10.0) / 10.0)
                            .reason(String.format("Only %.0f%% win rate over %d games", winRate, openingGames.size()))
                            .confidence(confidence)
                            .build();
                })
                .filter(r -> r.getWinRate() <= 35.0)
                .sorted(Comparator.comparingDouble(InsightsDto.OpeningRecommendation::getWinRate))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Analyze playstyle based on game termination types and length.
     */
    private InsightsDto.PlayStyle analyzePlayStyle(List<ChessGame> games) {
        double avgLength = games.stream()
                .filter(g -> g.getNumMoves() != null)
                .mapToInt(ChessGame::getNumMoves)
                .average()
                .orElse(0);

        long total = games.size();
        long checkmates = games.stream()
                .filter(g -> "checkmated".equals(g.getTermination()) || "win".equals(g.getResult()) && isCheckmate(g))
                .count();
        long resignations = games.stream()
                .filter(g -> "resigned".equals(g.getTermination()))
                .count();
        long timeouts = games.stream()
                .filter(g -> "timeout".equals(g.getTermination()))
                .count();
        long stalemates = games.stream()
                .filter(g -> "stalemate".equals(g.getTermination()))
                .count();

        String tendency;
        if (avgLength < 20) tendency = "Aggressive — you like short, sharp games";
        else if (avgLength < 35) tendency = "Balanced — mix of tactical and positional play";
        else if (avgLength < 50) tendency = "Positional — you prefer longer strategic games";
        else tendency = "Grinder — you play deep into the endgame";

        return InsightsDto.PlayStyle.builder()
                .avgGameLength(Math.round(avgLength * 10.0) / 10.0)
                .checkmateRate(total > 0 ? Math.round((double) checkmates / total * 1000.0) / 10.0 : 0)
                .resignRate(total > 0 ? Math.round((double) resignations / total * 1000.0) / 10.0 : 0)
                .timeoutRate(total > 0 ? Math.round((double) timeouts / total * 1000.0) / 10.0 : 0)
                .stalemateRate(total > 0 ? Math.round((double) stalemates / total * 1000.0) / 10.0 : 0)
                .tendency(tendency)
                .build();
    }

    private boolean isCheckmate(ChessGame game) {
        // Check if PGN ends with # (checkmate symbol)
        if (game.getPgn() == null) return false;
        String pgn = game.getPgn().trim();
        return pgn.contains("#");
    }

    /**
     * Find the opponent you've played the most games against.
     */
    private InsightsDto.RivalStats findTopRival(List<ChessGame> games) {
        Map<String, List<ChessGame>> byOpponent = games.stream()
                .collect(Collectors.groupingBy(g -> g.getOpponentUsername().toLowerCase()));

        return byOpponent.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .filter(e -> e.getValue().size() >= 2)
                .map(e -> {
                    List<ChessGame> rivalGames = e.getValue();
                    long wins = rivalGames.stream().filter(g -> "win".equals(g.getResult())).count();
                    long losses = rivalGames.stream().filter(g -> "loss".equals(g.getResult())).count();
                    long draws = rivalGames.stream().filter(g -> "draw".equals(g.getResult())).count();

                    // Find best opening against this rival
                    String bestOpening = rivalGames.stream()
                            .filter(g -> "win".equals(g.getResult()) && g.getOpeningName() != null)
                            .collect(Collectors.groupingBy(ChessGame::getOpeningName, Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("N/A");

                    return InsightsDto.RivalStats.builder()
                            .opponentUsername(e.getKey())
                            .totalGames(rivalGames.size())
                            .wins((int) wins)
                            .losses((int) losses)
                            .draws((int) draws)
                            .winRate(Math.round((double) wins / rivalGames.size() * 1000.0) / 10.0)
                            .bestOpening(bestOpening)
                            .build();
                })
                .orElse(null);
    }
}