package com.chessinsights.service;

import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.*;
import com.chessinsights.repository.GameRepository;
import com.chessinsights.repository.RepertoireLineRepository;
import com.chessinsights.repository.RepertoireRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeviationAnalysisService {

    private final RepertoireRepository repertoireRepo;
    private final RepertoireLineRepository lineRepo;
    private final GameRepository gameRepo;
    private final PgnParserService pgnParser;

    public DeviationAnalysisService(RepertoireRepository repertoireRepo,
                                     RepertoireLineRepository lineRepo,
                                     GameRepository gameRepo,
                                     PgnParserService pgnParser) {
        this.repertoireRepo = repertoireRepo;
        this.lineRepo = lineRepo;
        this.gameRepo = gameRepo;
        this.pgnParser = pgnParser;
    }

    /**
     * Finds games where the player deviated from their repertoire prep.
     *
     * Algorithm:
     * 1. Load all lines from the repertoire, build a move tree keyed by FEN -> expected SAN
     * 2. Find games matching this repertoire's color (white/black)
     * 3. For each game, replay the moves and check at each "your turn" position
     *    whether the move you played matches any repertoire line
     * 4. Return the first deviation point for each game
     */
    public List<DeviationResponse> findDeviations(User player, UUID repertoireId, int limit) {
        Repertoire repertoire = repertoireRepo.findByIdAndPlayer(repertoireId, player)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        // Build the repertoire move tree: FEN position -> set of expected moves (SAN)
        Map<String, Set<String>> moveTree = buildMoveTree(repertoire);
        if (moveTree.isEmpty()) {
            return List.of();
        }

        // Determine which color the player plays in this repertoire
        String colorFilter = repertoire.getColor() == Repertoire.Color.WHITE ? "white" : "black";

        // Fetch games where the player played this color
        List<ChessGame> games = gameRepo.findByUserAndUserColorOrderByPlayedAtDesc(player, colorFilter);

        List<DeviationResponse> deviations = new ArrayList<>();

        for (ChessGame game : games) {
            if (deviations.size() >= limit) break;
            if (game.getPgn() == null || game.getPgn().isBlank()) continue;

            DeviationResponse deviation = analyzeGame(game, repertoire, moveTree, colorFilter);
            if (deviation != null) {
                deviations.add(deviation);
            }
        }

        return deviations;
    }

    /**
     * Calculates how often the player followed their repertoire prep.
     *
     * Returns overall accuracy (% of moves matching prep) and per-line breakdown.
     */
    public AccuracyResponse calculateAccuracy(User player, UUID repertoireId) {
        Repertoire repertoire = repertoireRepo.findByIdAndPlayer(repertoireId, player)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        Map<String, Set<String>> moveTree = buildMoveTree(repertoire);
        if (moveTree.isEmpty()) {
            return new AccuracyResponse(0.0, 0, 0, List.of());
        }

        String colorFilter = repertoire.getColor() == Repertoire.Color.WHITE ? "white" : "black";
        List<ChessGame> games = gameRepo.findByUserAndUserColorOrderByPlayedAtDesc(player, colorFilter);

        int totalPrepMoves = 0;
        int totalMatchedMoves = 0;
        Map<String, int[]> perLineStats = new LinkedHashMap<>();

        // Initialize per-line counters
        for (RepertoireLine line : repertoire.getLines()) {
            perLineStats.put(line.getLineName(), new int[]{0, 0}); // [matched, total]
        }

        for (ChessGame game : games) {
            if (game.getPgn() == null || game.getPgn().isBlank()) continue;

            try {
                List<String[]> gameMoves = parseGameMoves(game.getPgn());
                String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
                boolean isPlayerTurn = colorFilter.equals("white");
                boolean stillInBook = true;

                com.github.bhlangonijr.chesslib.Board board = new com.github.bhlangonijr.chesslib.Board();

                for (String[] movePair : gameMoves) {
                    for (String san : movePair) {
                        if (san == null || san.isEmpty()) continue;

                        if (isPlayerTurn && stillInBook) {
                            Set<String> expectedMoves = moveTree.get(fen);
                            if (expectedMoves != null) {
                                totalPrepMoves++;
                                if (expectedMoves.contains(san)) {
                                    totalMatchedMoves++;
                                    updateLineStats(perLineStats, repertoire, fen, san, true);
                                } else {
                                    updateLineStats(perLineStats, repertoire, fen, san, false);
                                    stillInBook = false;
                                }
                            } else {
                                // Position not in any repertoire line — we're out of book
                                stillInBook = false;
                            }
                        }

                        // Execute the move to advance the position
                        try {
                            board.doMove(new com.github.bhlangonijr.chesslib.move.Move(san, board.getSideToMove()));
                            fen = board.getFen();
                        } catch (Exception e) {
                            stillInBook = false;
                            break;
                        }

                        isPlayerTurn = !isPlayerTurn;
                    }
                }
            } catch (Exception e) {
                // Skip games with unparseable PGN
            }
        }

        double overallAccuracy = totalPrepMoves > 0
                ? (double) totalMatchedMoves / totalPrepMoves * 100.0
                : 0.0;

        List<LineAccuracy> lineAccuracies = perLineStats.entrySet().stream()
                .map(e -> new LineAccuracy(
                        e.getKey(),
                        e.getValue()[1] > 0 ? (double) e.getValue()[0] / e.getValue()[1] * 100.0 : 0.0,
                        e.getValue()[0],
                        e.getValue()[1]
                ))
                .toList();

        return new AccuracyResponse(
                Math.round(overallAccuracy * 10.0) / 10.0,
                totalMatchedMoves,
                totalPrepMoves,
                lineAccuracies
        );
    }

    // ══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    /**
     * Builds a lookup tree from all repertoire lines.
     * Key: FEN position (before the move)
     * Value: set of acceptable SAN moves from that position
     *
     * This allows multiple lines to share early moves (transpositions)
     * and accepts any move that appears in any line.
     */
    private Map<String, Set<String>> buildMoveTree(Repertoire repertoire) {
        Map<String, Set<String>> tree = new HashMap<>();

        for (RepertoireLine line : repertoire.getLines()) {
            List<LineMove> moves = line.getMoves();
            if (moves.isEmpty()) continue;

            // The FEN before the first move is the starting position
            String prevFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

            for (LineMove move : moves) {
                tree.computeIfAbsent(prevFen, k -> new HashSet<>()).add(move.getMoveSan());
                prevFen = move.getFenAfter();
            }
        }

        return tree;
    }

    /**
     * Analyzes a single game against the repertoire move tree.
     * Returns the first point of deviation, or null if the game followed prep
     * (or if the game never entered a repertoire position).
     */
    private DeviationResponse analyzeGame(ChessGame game, Repertoire repertoire,
                                           Map<String, Set<String>> moveTree, String colorFilter) {
        try {
            List<String[]> gameMoves = parseGameMoves(game.getPgn());
            com.github.bhlangonijr.chesslib.Board board = new com.github.bhlangonijr.chesslib.Board();
            String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            boolean isPlayerTurn = colorFilter.equals("white");
            int moveNumber = 0;
            boolean enteredPrep = false;

            for (String[] movePair : gameMoves) {
                moveNumber++;
                for (int i = 0; i < movePair.length; i++) {
                    String san = movePair[i];
                    if (san == null || san.isEmpty()) continue;

                    if (isPlayerTurn) {
                        Set<String> expectedMoves = moveTree.get(fen);
                        if (expectedMoves != null) {
                            enteredPrep = true;
                            if (!expectedMoves.contains(san)) {
                                // Found a deviation
                                String expectedStr = String.join(" or ", expectedMoves);
                                return new DeviationResponse(
                                        game.getId(),
                                        game.getOpponentUsername(),
                                        game.getPlayedAt(),
                                        findMatchingLineName(repertoire, fen),
                                        moveNumber,
                                        expectedStr,
                                        san,
                                        game.getResult()
                                );
                            }
                        } else if (enteredPrep) {
                            // Was in prep but this position isn't covered — end of repertoire line
                            return null;
                        }
                    }

                    // Execute the move
                    try {
                        board.doMove(new com.github.bhlangonijr.chesslib.move.Move(san, board.getSideToMove()));
                        fen = board.getFen();
                    } catch (Exception e) {
                        return null;
                    }

                    isPlayerTurn = !isPlayerTurn;
                }
            }
        } catch (Exception e) {
            // Unparseable game
        }

        return null; // No deviation found (or game never entered prep territory)
    }

    /**
     * Finds the repertoire line name that contains the given FEN position.
     */
    private String findMatchingLineName(Repertoire repertoire, String fen) {
        for (RepertoireLine line : repertoire.getLines()) {
            for (LineMove move : line.getMoves()) {
                if (move.getFenAfter().equals(fen)) {
                    return line.getLineName();
                }
            }
            // Also check if the starting position matches
            if (fen.equals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
                    && !line.getMoves().isEmpty()) {
                return line.getLineName();
            }
        }
        return "Unknown line";
    }

    /**
     * Parses PGN text into move pairs [whiteSan, blackSan].
     * Handles standard PGN format: "1. e4 e5 2. Nf3 Nc6 ..."
     */
    private List<String[]> parseGameMoves(String pgn) {
        String cleaned = pgn
                .replaceAll("\\{[^}]*}", "")        // Remove comments
                .replaceAll("\\$\\d+", "")            // Remove NAGs
                .replaceAll("\\s*(1-0|0-1|1/2-1/2|\\*)\\s*$", "") // Remove result
                .replaceAll("\\d+\\.{1,3}\\s*", "")  // Remove move numbers
                .replaceAll("\\s+", " ")
                .trim();

        String[] sans = cleaned.split(" ");
        List<String[]> movePairs = new ArrayList<>();

        for (int i = 0; i < sans.length; i += 2) {
            String white = sans[i];
            String black = (i + 1 < sans.length) ? sans[i + 1] : null;
            movePairs.add(new String[]{white, black});
        }

        return movePairs;
    }

    /**
     * Updates per-line accuracy stats.
     */
    private void updateLineStats(Map<String, int[]> perLineStats, Repertoire repertoire,
                                  String fen, String san, boolean matched) {
        String lineName = findMatchingLineName(repertoire, fen);
        int[] stats = perLineStats.get(lineName);
        if (stats != null) {
            stats[1]++; // total
            if (matched) stats[0]++; // matched
        }
    }
}