package com.chessinsights.service;

import com.chessinsights.entity.LineMove;
import com.chessinsights.entity.RepertoireLine;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PgnParserService {

    /**
     * Parses a PGN move text string into a list of LineMove entities.
     *
     * Accepts move text in several formats:
     *   - Standard PGN: "1. d4 d5 2. c4 Nc6 3. Nf3 Nf6"
     *   - Space-separated SAN: "d4 d5 c4 Nc6 Nf3 Nf6"
     *   - With or without move numbers, comments, result markers
     *
     * @param line the RepertoireLine parent entity
     * @param pgn  the PGN move text to parse
     * @return list of LineMove entities with FEN and UCI for each move
     */
    public List<LineMove> parse(RepertoireLine line, String pgn) {
        String cleaned = cleanPgn(pgn);

        MoveList moveList = new MoveList();
        moveList.loadFromSan(cleaned);

        Board board = new Board();
        List<LineMove> result = new ArrayList<>();
        int moveNumber = 0;

        for (Move move : moveList) {
            moveNumber++;

            // Get SAN notation before executing the move
            String san = move.getSan();

            // Convert to UCI notation (e.g., "e2e4", "g1f3")
            String uci = toUci(move);

            // Execute the move on the board
            board.doMove(move);

            // Capture the FEN after this move
            String fenAfter = board.getFen();

            LineMove lineMove = new LineMove(line, moveNumber, uci, san, fenAfter);
            result.add(lineMove);
        }

        return result;
    }

    /**
     * Validates that a PGN string can be parsed without errors.
     *
     * @param pgn the PGN move text to validate
     * @return true if the PGN is valid
     */
    public boolean isValid(String pgn) {
        try {
            String cleaned = cleanPgn(pgn);
            MoveList moveList = new MoveList();
            moveList.loadFromSan(cleaned);
            return !moveList.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the FEN position after playing all moves in the PGN.
     *
     * @param pgn the PGN move text
     * @return the final FEN position
     */
    public String getFinalFen(String pgn) {
        String cleaned = cleanPgn(pgn);
        MoveList moveList = new MoveList();
        moveList.loadFromSan(cleaned);

        Board board = new Board();
        for (Move move : moveList) {
            board.doMove(move);
        }
        return board.getFen();
    }

    /**
     * Cleans PGN text by removing move numbers, result markers,
     * comments, and normalizing whitespace.
     *
     * Input:  "1. d4 d5 2. c4 Nc6 3. Nf3 {main line} Nf6 1-0"
     * Output: "d4 d5 c4 Nc6 Nf3 Nf6"
     */
    private String cleanPgn(String pgn) {
        String cleaned = pgn;

        // Remove comments in curly braces: {this is a comment}
        cleaned = cleaned.replaceAll("\\{[^}]*}", "");

        // Remove comments after semicolons (to end of line)
        cleaned = cleaned.replaceAll(";[^\n]*", "");

        // Remove Numeric Annotation Glyphs: $1, $14, etc.
        cleaned = cleaned.replaceAll("\\$\\d+", "");

        // Remove result markers at the end
        cleaned = cleaned.replaceAll("\\s*(1-0|0-1|1/2-1/2|\\*)\\s*$", "");

        // Remove move numbers: "1." "1..." "12." "12..."
        cleaned = cleaned.replaceAll("\\d+\\.{1,3}\\s*", "");

        // Normalize whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    /**
     * Converts a chesslib Move to UCI notation.
     * UCI format is simply the from-square + to-square in lowercase,
     * with an optional promotion piece suffix.
     *
     * Examples: "e2e4", "g1f3", "e7e8q" (pawn promotes to queen)
     */
    private String toUci(Move move) {
    String from = move.getFrom().value().toLowerCase();
    String to = move.getTo().value().toLowerCase();
    String uci = from + to;

    if (move.getPromotion() != null
            && move.getPromotion() != com.github.bhlangonijr.chesslib.Piece.NONE) {
        String pieceName = move.getPromotion().name();
        char promoChar;
        if (pieceName.contains("QUEEN")) promoChar = 'q';
        else if (pieceName.contains("ROOK")) promoChar = 'r';
        else if (pieceName.contains("BISHOP")) promoChar = 'b';
        else if (pieceName.contains("KNIGHT")) promoChar = 'n';
        else promoChar = 'q';
        uci += promoChar;
    }

    return uci;
}

}