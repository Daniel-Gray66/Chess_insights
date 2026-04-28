package com.chessinsights.service;

import com.chessinsights.client.ChessComClient;
import com.chessinsights.entity.ChessGame;
import com.chessinsights.entity.SyncJob;
import com.chessinsights.entity.User;
import com.chessinsights.repository.GameRepository;
import com.chessinsights.repository.SyncJobRepository;
import com.chessinsights.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles syncing games from Chess.com into our database.
 *
 * Flow:
 * 1. Fetch the user's archive list from Chess.com
 * 2. For each monthly archive, fetch all games
 * 3. Parse each game JSON and map to our ChessGame entity
 * 4. Skip duplicates (by chess_com_game_id)
 * 5. Track progress in a SyncJob record
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameSyncService {

    private final ChessComClient chessComClient;
    private final GameRepository gameRepository;
    private final SyncJobRepository syncJobRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Sync all games for a user. Called manually via endpoint or by the scheduler.
     */
    @Transactional
    public SyncJob syncGames(User user) {
        // Prevent concurrent syncs for the same user
        if (syncJobRepository.existsByUserAndStatus(user, "RUNNING")) {
            throw new IllegalStateException("A sync is already running for this user");
        }

        SyncJob job = SyncJob.builder()
                .user(user)
                .startedAt(Instant.now())
                .status("RUNNING")
                .gamesFetched(0)
                .newGamesSaved(0)
                .archivesProcessed(0)
                .build();
        syncJobRepository.save(job);

        try {
            List<String> archives = chessComClient.getArchives(user.getChessComUsername());
            int totalFetched = 0;
            int totalNew = 0;

            for (String archiveUrl : archives) {
                log.info("Processing archive: {}", archiveUrl);

                JsonNode archiveData = chessComClient.getGamesForMonth(archiveUrl);
                JsonNode gamesArray = archiveData.get("games");

                if (gamesArray == null || !gamesArray.isArray()) {
                    continue;
                }

                for (JsonNode gameJson : gamesArray) {
                    totalFetched++;
                    try {
                        boolean saved = processGame(user, gameJson);
                        if (saved) totalNew++;
                    } catch (Exception e) {
                        log.warn("Failed to process game: {}", e.getMessage());
                    }
                }

                job.setArchivesProcessed(job.getArchivesProcessed() + 1);

                // Be respectful to Chess.com API - small delay between archive requests
                Thread.sleep(500);
            }

            job.setGamesFetched(totalFetched);
            job.setNewGamesSaved(totalNew);
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());

            user.setLastSyncedAt(Instant.now());
            userRepository.save(user);

            log.info("Sync completed for '{}': {} fetched, {} new",
                    user.getChessComUsername(), totalFetched, totalNew);

        } catch (Exception e) {
            log.error("Sync failed for '{}': {}", user.getChessComUsername(), e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
        }

        return syncJobRepository.save(job);
    }

    /**
     * Parse a single game JSON object from Chess.com and persist it.
     * Returns true if a new game was saved, false if it was a duplicate.
     *
     * Chess.com game JSON structure:
     * {
     *   "url": "https://www.chess.com/game/live/12345",
     *   "pgn": "[Event ...] 1. e4 e5 ...",
     *   "time_control": "600",
     *   "time_class": "rapid",
     *   "rules": "chess",
     *   "white": { "username": "player1", "rating": 1200, "result": "win", ... },
     *   "black": { "username": "player2", "rating": 1150, "result": "checkmated", ... },
     *   "end_time": 1700000000,
     *   "accuracies": { "white": 85.5, "black": 72.3 }  // optional
     * }
     */
    private boolean processGame(User user, JsonNode gameJson) {
        String gameUrl = gameJson.has("url") ? gameJson.get("url").asText() : null;

        // Extract a unique game ID from the URL (last segment)
        String gameId = extractGameId(gameUrl);
        if (gameId == null) return false;

        // Skip if we already have this game
        if (gameRepository.existsByUserAndChessComGameId(user, gameId)) {
            return false;
        }

        String chessComUsername = user.getChessComUsername().toLowerCase();
        JsonNode whiteNode = gameJson.get("white");
        JsonNode blackNode = gameJson.get("black");

        if (whiteNode == null || blackNode == null) return false;

        String whiteUsername = whiteNode.get("username").asText().toLowerCase();
        boolean userIsWhite = whiteUsername.equals(chessComUsername);

        JsonNode userNode = userIsWhite ? whiteNode : blackNode;
        JsonNode opponentNode = userIsWhite ? blackNode : whiteNode;

        String chessComResult = userNode.get("result").asText();
        String normalizedResult = normalizeResult(chessComResult);

        // Parse opening info from PGN headers if available
        String pgn = gameJson.has("pgn") ? gameJson.get("pgn").asText() : null;
        String ecoCode = extractPgnHeader(pgn, "ECO");
        String openingName = extractPgnHeader(pgn, "ECOUrl");
        if (openingName != null) {
            // ECOUrl looks like "https://www.chess.com/openings/Sicilian-Defense..."
            // Extract just the opening name part
            openingName = cleanOpeningName(openingName);
        }

        // Get accuracy if available
        Double accuracy = null;
        if (gameJson.has("accuracies")) {
            JsonNode accuracies = gameJson.get("accuracies");
            String colorKey = userIsWhite ? "white" : "black";
            if (accuracies.has(colorKey)) {
                accuracy = accuracies.get(colorKey).asDouble();
            }
        }

        long endTime = gameJson.has("end_time") ? gameJson.get("end_time").asLong() : 0;

        ChessGame game = ChessGame.builder()
                .user(user)
                .chessComGameId(gameId)
                .playedAt(Instant.ofEpochSecond(endTime))
                .timeClass(gameJson.has("time_class") ? gameJson.get("time_class").asText() : "unknown")
                .timeControl(gameJson.has("time_control") ? gameJson.get("time_control").asText() : null)
                .userColor(userIsWhite ? "white" : "black")
                .userRating(userNode.get("rating").asInt())
                .opponentUsername(opponentNode.get("username").asText())
                .opponentRating(opponentNode.get("rating").asInt())
                .result(normalizedResult)
                .termination(chessComResult)
                .ecoCode(ecoCode)
                .openingName(openingName)
                .pgn(pgn)
                .numMoves(countMoves(pgn))
                .accuracy(accuracy)
                .gameUrl(gameUrl)
                .rawJson(gameJson.toString())
                .build();

        gameRepository.save(game);
        return true;
    }

    /**
     * Chess.com uses specific result codes. Normalize to win/loss/draw.
     *
     * Win results: "win"
     * Loss results: "checkmated", "timeout", "resigned", "lose", "abandoned"
     * Draw results: "agreed", "stalemate", "repetition", "insufficient",
     *               "50move", "timevsinsufficient"
     */
    private String normalizeResult(String chessComResult) {
        if ("win".equals(chessComResult)) {
            return "win";
        }

        return switch (chessComResult) {
            case "checkmated", "timeout", "resigned", "lose", "abandoned" -> "loss";
            case "agreed", "stalemate", "repetition", "insufficient",
                 "50move", "timevsinsufficient" -> "draw";
            default -> "loss"; // default to loss for unknown codes
        };
    }

    /**
     * Extract game ID from Chess.com game URL.
     * URL format: https://www.chess.com/game/live/12345678
     */
    private String extractGameId(String url) {
        if (url == null || url.isEmpty()) return null;
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    /**
     * Extract a header value from PGN text.
     * PGN headers look like: [ECO "B20"]
     */
    private String extractPgnHeader(String pgn, String header) {
        if (pgn == null) return null;
        String prefix = "[" + header + " \"";
        int start = pgn.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = pgn.indexOf("\"", start);
        if (end == -1) return null;
        return pgn.substring(start, end);
    }

    /**
     * Clean opening name from Chess.com ECOUrl format.
     * Input:  "https://www.chess.com/openings/Sicilian-Defense-Closed-2...Nc6"
     * Output: "Sicilian Defense"
     */
    private String cleanOpeningName(String ecoUrl) {
        if (ecoUrl == null) return null;
        // Get the part after "/openings/"
        int idx = ecoUrl.indexOf("/openings/");
        if (idx == -1) return ecoUrl;
        String name = ecoUrl.substring(idx + "/openings/".length());
        // Replace hyphens with spaces and clean up
        name = name.replace("-", " ");
        // Truncate at numbers or very long variant names for readability
        // Keep the main opening name (first 3-4 words typically)
        return name.length() > 60 ? name.substring(0, 60) + "..." : name;
    }

    /**
     * Count the number of moves in a PGN string.
     * Simple heuristic: count move numbers like "1.", "2.", etc.
     */
    private Integer countMoves(String pgn) {
        if (pgn == null) return null;
        // Find the last move number
        int lastMoveNum = 0;
        String[] tokens = pgn.split("\\s+");
        for (String token : tokens) {
            if (token.matches("\\d+\\.")) {
                try {
                    int num = Integer.parseInt(token.replace(".", ""));
                    if (num > lastMoveNum) lastMoveNum = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        return lastMoveNum > 0 ? lastMoveNum : null;
    }
}
