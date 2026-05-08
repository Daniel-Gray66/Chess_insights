package com.chessinsights.service;

import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.*;
import com.chessinsights.repository.OpeningRepository;
import com.chessinsights.repository.RepertoireLineRepository;
import com.chessinsights.repository.RepertoireRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepertoireServiceTest {

    @Mock
    private RepertoireRepository repertoireRepo;

    @Mock
    private RepertoireLineRepository lineRepo;

    @Mock
    private OpeningRepository openingRepo;

    @Spy
    private PgnParserService pgnParser = new PgnParserService();

    @InjectMocks
    private RepertoireService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("daniel");
        testUser.setChessComUsername("danielchess");
    }

    // ══════════════════════════════════════════════════════════
    //  REPERTOIRE CRUD
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createRepertoire()")
    class CreateRepertoireTests {

        @Test
        @DisplayName("creates repertoire with correct fields")
        void createsRepertoire() {
            CreateRepertoireRequest request = new CreateRepertoireRequest(
                    "Chigorin QGD", Repertoire.Color.BLACK, "1.d4", "Chigorin variation vs d4"
            );

            when(repertoireRepo.save(any(Repertoire.class))).thenAnswer(inv -> {
                Repertoire r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            RepertoireResponse response = service.createRepertoire(testUser, request);

            assertNotNull(response.id());
            assertEquals("Chigorin QGD", response.name());
            assertEquals(Repertoire.Color.BLACK, response.color());
            assertEquals("1.d4", response.rootMove());
            verify(repertoireRepo).save(any(Repertoire.class));
        }
    }

    @Nested
    @DisplayName("listRepertoires()")
    class ListRepertoiresTests {

        @Test
        @DisplayName("returns all repertoires when no color filter")
        void listsAll() {
            Repertoire r1 = createTestRepertoire("Sicilian", Repertoire.Color.BLACK);
            Repertoire r2 = createTestRepertoire("Italian", Repertoire.Color.WHITE);
            when(repertoireRepo.findByPlayer(testUser)).thenReturn(List.of(r1, r2));

            List<RepertoireResponse> result = service.listRepertoires(testUser, null);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("filters by color when specified")
        void filtersByColor() {
            Repertoire r1 = createTestRepertoire("Sicilian", Repertoire.Color.BLACK);
            when(repertoireRepo.findByPlayerAndColor(testUser, Repertoire.Color.BLACK))
                    .thenReturn(List.of(r1));

            List<RepertoireResponse> result = service.listRepertoires(testUser, Repertoire.Color.BLACK);

            assertEquals(1, result.size());
            assertEquals("Sicilian", result.get(0).name());
        }
    }

    @Nested
    @DisplayName("deleteRepertoire()")
    class DeleteRepertoireTests {

        @Test
        @DisplayName("deletes existing repertoire")
        void deletesRepertoire() {
            UUID id = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Sicilian", Repertoire.Color.BLACK);
            when(repertoireRepo.findByIdAndPlayer(id, testUser)).thenReturn(Optional.of(repertoire));

            service.deleteRepertoire(testUser, id);

            verify(repertoireRepo).delete(repertoire);
        }

        @Test
        @DisplayName("throws when repertoire not found")
        void throwsOnNotFound() {
            UUID id = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(id, testUser)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> service.deleteRepertoire(testUser, id));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LINE CRUD
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addLine()")
    class AddLineTests {

        @Test
        @DisplayName("adds line with parsed moves")
        void addsLineWithMoves() {
            UUID repId = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Chigorin", Repertoire.Color.BLACK);
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(repertoire));
            when(lineRepo.save(any(RepertoireLine.class))).thenAnswer(inv -> {
                RepertoireLine l = inv.getArgument(0);
                l.setId(UUID.randomUUID());
                return l;
            });

            AddLineRequest request = new AddLineRequest(
                    "Main line", "1. d4 d5 2. c4 Nc6", "Key line", null, null
            );

            LineResponse response = service.addLine(testUser, repId, request);

            assertEquals("Main line", response.lineName());
            assertEquals(5, response.drillPriority()); // default
            verify(pgnParser).parse(any(), eq("1. d4 d5 2. c4 Nc6"));
        }

        @Test
        @DisplayName("rejects invalid PGN")
        void rejectsInvalidPgn() {
            UUID repId = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Test", Repertoire.Color.WHITE);
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(repertoire));

            AddLineRequest request = new AddLineRequest(
                    "Bad line", "this is not chess", "notes", null, null
            );

            assertThrows(IllegalArgumentException.class,
                    () -> service.addLine(testUser, repId, request));
        }

        @Test
        @DisplayName("sets custom drill priority when provided")
        void setsCustomPriority() {
            UUID repId = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Test", Repertoire.Color.WHITE);
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(repertoire));
            when(lineRepo.save(any(RepertoireLine.class))).thenAnswer(inv -> {
                RepertoireLine l = inv.getArgument(0);
                l.setId(UUID.randomUUID());
                return l;
            });

            AddLineRequest request = new AddLineRequest(
                    "Priority line", "1. e4 e5", "notes", 8, null
            );

            LineResponse response = service.addLine(testUser, repId, request);

            assertEquals(8, response.drillPriority());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DRILL MODE
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getNextDrill()")
    class DrillTests {

        @Test
        @DisplayName("returns a drill position from the repertoire")
        void returnsDrillPosition() {
            UUID repId = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Test", Repertoire.Color.WHITE);
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(repertoire));

            RepertoireLine line = createTestLineWithMoves("Main line", "1. e4 e5 2. Nf3 Nc6");
            when(lineRepo.findDrillCandidates(repId)).thenReturn(List.of(line));

            DrillResponse response = service.getNextDrill(testUser, repId);

            assertNotNull(response);
            assertEquals(line.getId(), response.lineId());
            assertEquals("Main line", response.lineName());
            assertNotNull(response.fenAtPosition());
            assertNotNull(response.expectedMoveSan());
            assertNotNull(response.expectedMoveUci());
        }

        @Test
        @DisplayName("throws when no lines available")
        void throwsOnNoLines() {
            UUID repId = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(
                    createTestRepertoire("Empty", Repertoire.Color.WHITE)));
            when(lineRepo.findDrillCandidates(repId)).thenReturn(List.of());

            assertThrows(IllegalStateException.class,
                    () -> service.getNextDrill(testUser, repId));
        }

        @Test
        @DisplayName("handles single-move line by quizzing from starting position")
        void handlesSingleMoveLine() {
            UUID repId = UUID.randomUUID();
            Repertoire repertoire = createTestRepertoire("Test", Repertoire.Color.WHITE);
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(repertoire));

            RepertoireLine line = createTestLineWithMoves("One move", "1. e4");
            when(lineRepo.findDrillCandidates(repId)).thenReturn(List.of(line));

            DrillResponse response = service.getNextDrill(testUser, repId);

            assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    response.fenAtPosition());
            assertEquals("e4", response.expectedMoveSan());
        }
    }

    @Nested
    @DisplayName("recordDrillResult()")
    class DrillResultTests {

        @Test
        @DisplayName("correct answer lowers drill priority")
        void correctLowersPriority() {
            UUID repId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(
                    createTestRepertoire("Test", Repertoire.Color.WHITE)));

            RepertoireLine line = new RepertoireLine();
            line.setId(lineId);
            line.setDrillPriority(5);
            line.setTimesDrilled(3);
            when(lineRepo.findByIdAndRepertoireId(lineId, repId)).thenReturn(Optional.of(line));
            when(lineRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordDrillResult(testUser, repId, new DrillResultRequest(lineId, true));

            assertEquals(4, line.getDrillPriority());
            assertEquals(4, line.getTimesDrilled());
            assertNotNull(line.getLastDrilledAt());
        }

        @Test
        @DisplayName("incorrect answer raises drill priority")
        void incorrectRaisesPriority() {
            UUID repId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(
                    createTestRepertoire("Test", Repertoire.Color.WHITE)));

            RepertoireLine line = new RepertoireLine();
            line.setId(lineId);
            line.setDrillPriority(5);
            line.setTimesDrilled(3);
            when(lineRepo.findByIdAndRepertoireId(lineId, repId)).thenReturn(Optional.of(line));
            when(lineRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordDrillResult(testUser, repId, new DrillResultRequest(lineId, false));

            assertEquals(7, line.getDrillPriority());
        }

        @Test
        @DisplayName("priority does not go below 1")
        void priorityFloorIsOne() {
            UUID repId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(
                    createTestRepertoire("Test", Repertoire.Color.WHITE)));

            RepertoireLine line = new RepertoireLine();
            line.setId(lineId);
            line.setDrillPriority(1);
            when(lineRepo.findByIdAndRepertoireId(lineId, repId)).thenReturn(Optional.of(line));
            when(lineRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordDrillResult(testUser, repId, new DrillResultRequest(lineId, true));

            assertEquals(1, line.getDrillPriority());
        }

        @Test
        @DisplayName("priority does not go above 10")
        void priorityCeilingIsTen() {
            UUID repId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            when(repertoireRepo.findByIdAndPlayer(repId, testUser)).thenReturn(Optional.of(
                    createTestRepertoire("Test", Repertoire.Color.WHITE)));

            RepertoireLine line = new RepertoireLine();
            line.setId(lineId);
            line.setDrillPriority(9);
            when(lineRepo.findByIdAndRepertoireId(lineId, repId)).thenReturn(Optional.of(line));
            when(lineRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordDrillResult(testUser, repId, new DrillResultRequest(lineId, false));

            assertEquals(10, line.getDrillPriority());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ══════════════════════════════════════════════════════════

    private Repertoire createTestRepertoire(String name, Repertoire.Color color) {
        Repertoire r = new Repertoire(testUser, name, color, "1.e4", "Test repertoire");
        r.setId(UUID.randomUUID());
        return r;
    }

    private RepertoireLine createTestLineWithMoves(String name, String pgn) {
        RepertoireLine line = new RepertoireLine();
        line.setId(UUID.randomUUID());
        line.setLineName(name);
        line.setPgn(pgn);
        line.setDrillPriority(5);

        // Actually parse the PGN to create real moves
        List<LineMove> moves = pgnParser.parse(line, pgn);
        line.getMoves().addAll(moves);

        // Create a minimal repertoire reference for the drill response
        Repertoire rep = new Repertoire();
        rep.setName("Test Repertoire");
        line.setRepertoire(rep);

        return line;
    }
}
