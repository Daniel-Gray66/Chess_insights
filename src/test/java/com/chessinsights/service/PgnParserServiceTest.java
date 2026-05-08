package com.chessinsights.service;

import com.chessinsights.entity.LineMove;
import com.chessinsights.entity.RepertoireLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PgnParserServiceTest {

    private PgnParserService parser;

    @BeforeEach
    void setUp() {
        parser = new PgnParserService();
    }

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("parses standard PGN with move numbers")
        void parsesStandardPgn() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(6, moves.size());

            // First move: e4
            assertEquals("e4", moves.get(0).getMoveSan());
            assertEquals("e2e4", moves.get(0).getMoveUci());
            assertEquals(1, moves.get(0).getMoveNumber());
            assertNotNull(moves.get(0).getFenAfter());

            // Second move: e5
            assertEquals("e5", moves.get(1).getMoveSan());
            assertEquals("e7e5", moves.get(1).getMoveUci());

            // Third move: Nf3
            assertEquals("Nf3", moves.get(2).getMoveSan());
            assertEquals("g1f3", moves.get(2).getMoveUci());

            // Fourth move: Nc6
            assertEquals("Nc6", moves.get(3).getMoveSan());
            assertEquals("b8c6", moves.get(3).getMoveUci());
        }

        @Test
        @DisplayName("parses space-separated SAN without move numbers")
        void parsesBareSan() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "d4 d5 c4 Nc6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(4, moves.size());
            assertEquals("d4", moves.get(0).getMoveSan());
            assertEquals("d5", moves.get(1).getMoveSan());
            assertEquals("c4", moves.get(2).getMoveSan());
            assertEquals("Nc6", moves.get(3).getMoveSan());
        }

        @Test
        @DisplayName("strips comments in curly braces")
        void stripsComments() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. e4 {best move} e5 2. Nf3 {developing} Nc6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(4, moves.size());
            assertEquals("e4", moves.get(0).getMoveSan());
            assertEquals("e5", moves.get(1).getMoveSan());
        }

        @Test
        @DisplayName("strips result markers")
        void stripsResultMarkers() {
            RepertoireLine line = new RepertoireLine();

            assertEquals(4, parser.parse(line, "1. e4 e5 2. Nf3 Nc6 1-0").size());
            assertEquals(4, parser.parse(line, "1. e4 e5 2. Nf3 Nc6 0-1").size());
            assertEquals(4, parser.parse(line, "1. e4 e5 2. Nf3 Nc6 1/2-1/2").size());
            assertEquals(4, parser.parse(line, "1. e4 e5 2. Nf3 Nc6 *").size());
        }

        @Test
        @DisplayName("strips NAG symbols")
        void stripsNags() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. e4 $1 e5 $2 2. Nf3 Nc6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(4, moves.size());
        }

        @Test
        @DisplayName("sets correct parent reference on each move")
        void setsParentReference() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. e4 e5";

            List<LineMove> moves = parser.parse(line, pgn);

            for (LineMove move : moves) {
                assertSame(line, move.getLine());
            }
        }

        @Test
        @DisplayName("move numbers are sequential starting from 1")
        void moveNumbersSequential() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. d4 d5 2. c4 Nc6 3. Nf3 Nf6";

            List<LineMove> moves = parser.parse(line, pgn);

            for (int i = 0; i < moves.size(); i++) {
                assertEquals(i + 1, moves.get(i).getMoveNumber());
            }
        }

        @Test
        @DisplayName("FEN after each move represents a valid position")
        void fenIsValid() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. e4 e5 2. Nf3 Nc6";

            List<LineMove> moves = parser.parse(line, pgn);

            for (LineMove move : moves) {
                String fen = move.getFenAfter();
                assertNotNull(fen);
                // FEN has 6 space-separated fields
                assertEquals(6, fen.split(" ").length, "FEN should have 6 fields: " + fen);
                // FEN board has 8 ranks separated by /
                assertEquals(8, fen.split(" ")[0].split("/").length, "FEN board should have 8 ranks: " + fen);
            }
        }

        @Test
        @DisplayName("parses Chigorin QGD line correctly")
        void parsesChigorinLine() {
            RepertoireLine line = new RepertoireLine();
            String pgn = "1. d4 d5 2. c4 Nc6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(4, moves.size());
            assertEquals("d4", moves.get(0).getMoveSan());
            assertEquals("d2d4", moves.get(0).getMoveUci());
            assertEquals("d5", moves.get(1).getMoveSan());
            assertEquals("d7d5", moves.get(1).getMoveUci());
            assertEquals("c4", moves.get(2).getMoveSan());
            assertEquals("c2c4", moves.get(2).getMoveUci());
            assertEquals("Nc6", moves.get(3).getMoveSan());
            assertEquals("b8c6", moves.get(3).getMoveUci());
        }

        @Test
        @DisplayName("parses castling moves")
        void parsesCastling() {
            RepertoireLine line = new RepertoireLine();
            // Italian Game into short castle
            String pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O Nf6";

            List<LineMove> moves = parser.parse(line, pgn);

            assertEquals(8, moves.size());
            assertEquals("O-O", moves.get(6).getMoveSan());
            // Kingside castle UCI: e1g1
            assertEquals("e1g1", moves.get(6).getMoveUci());
        }
    }

    @Nested
    @DisplayName("isValid()")
    class ValidationTests {

        @Test
        @DisplayName("returns true for valid PGN")
        void validPgn() {
            assertTrue(parser.isValid("1. e4 e5 2. Nf3 Nc6"));
        }

        @Test
        @DisplayName("returns true for bare SAN")
        void validBareSan() {
            assertTrue(parser.isValid("e4 e5 Nf3 Nc6"));
        }

        @Test
        @DisplayName("returns false for illegal moves")
        void invalidMoves() {
            assertFalse(parser.isValid("1. e4 e5 2. Nf3 Nf3"));
        }

        @Test
        @DisplayName("returns false for empty string")
        void emptyString() {
            assertFalse(parser.isValid(""));
        }

        @Test
        @DisplayName("returns false for garbage input")
        void garbageInput() {
            assertFalse(parser.isValid("hello world this is not chess"));
        }
    }

    @Nested
    @DisplayName("getFinalFen()")
    class FinalFenTests {

        @Test
        @DisplayName("returns starting position FEN for no moves")
        void startingPosition() {
            // After "1. e4", the FEN should show pawn on e4
            String fen = parser.getFinalFen("1. e4");
            assertTrue(fen.contains("4P3"), "FEN should show pawn on e4: " + fen);
        }

        @Test
        @DisplayName("returns correct FEN after Chigorin setup")
        void chigorinPosition() {
            String fen = parser.getFinalFen("1. d4 d5 2. c4 Nc6");
            // Black knight should be on c6
            assertNotNull(fen);
            // Side to move should be white after 4 half-moves
            assertTrue(fen.contains(" w "), "Should be white to move: " + fen);
        }
    }
}
