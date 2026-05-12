package com.chessinsights.service;

import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.*;
import com.chessinsights.repository.LineMoveRepository;
import com.chessinsights.repository.OpeningRepository;
import com.chessinsights.repository.RepertoireLineRepository;
import com.chessinsights.repository.RepertoireRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RepertoireService {

    private final RepertoireRepository repertoireRepo;
    private final RepertoireLineRepository lineRepo;
    private final LineMoveRepository moveRepo;
    private final OpeningRepository openingRepo;
    private final PgnParserService pgnParser;

    public RepertoireService(RepertoireRepository repertoireRepo,
                             RepertoireLineRepository lineRepo,
                             LineMoveRepository moveRepo,
                             OpeningRepository openingRepo,
                             PgnParserService pgnParser) {
        this.repertoireRepo = repertoireRepo;
        this.lineRepo = lineRepo;
        this.moveRepo = moveRepo;
        this.openingRepo = openingRepo;
        this.pgnParser = pgnParser;
    }

    // ══════════════════════════════════════════════════════════
    //  REPERTOIRE CRUD
    // ══════════════════════════════════════════════════════════

    public List<RepertoireResponse> listRepertoires(User player, Repertoire.Color color) {
        List<Repertoire> repertoires = (color != null)
                ? repertoireRepo.findByPlayerAndColor(player, color)
                : repertoireRepo.findByPlayer(player);

        return repertoires.stream()
                .map(this::toRepertoireResponse)
                .toList();
    }

    @Transactional
    public RepertoireResponse createRepertoire(User player, CreateRepertoireRequest request) {
        Repertoire repertoire = new Repertoire(
                player,
                request.name(),
                request.color(),
                request.rootMove(),
                request.description()
        );
        Repertoire saved = repertoireRepo.save(repertoire);
        return toRepertoireResponse(saved);
    }

    public RepertoireDetailResponse getRepertoireDetail(User player, UUID repertoireId) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);
        return toRepertoireDetailResponse(repertoire);
    }

    @Transactional
    public void deleteRepertoire(User player, UUID repertoireId) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);
        repertoireRepo.delete(repertoire);
    }

    // ══════════════════════════════════════════════════════════
    //  LINE CRUD
    // ══════════════════════════════════════════════════════════

    @Transactional
    public LineResponse addLine(User player, UUID repertoireId, AddLineRequest request) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);

        if (!pgnParser.isValid(request.pgn())) {
            throw new IllegalArgumentException("Invalid PGN: " + request.pgn());
        }

        RepertoireLine line = new RepertoireLine(
                repertoire,
                request.lineName(),
                request.pgn(),
                request.notes()
        );

        if (request.drillPriority() != null) {
            line.setDrillPriority(request.drillPriority());
        }

        if (request.openingId() != null) {
            Opening opening = openingRepo.findById(request.openingId())
                    .orElseThrow(() -> new EntityNotFoundException("Opening not found: " + request.openingId()));
            line.setOpening(opening);
        }

        List<LineMove> moves = pgnParser.parse(line, request.pgn());
        line.getMoves().addAll(moves);

        repertoire.getLines().add(line);
        RepertoireLine saved = lineRepo.save(line);
        return toLineResponse(saved);
    }

    @Transactional
    public LineResponse updateLine(User player, UUID repertoireId, UUID lineId, UpdateLineRequest request) {
        findRepertoireForPlayer(player, repertoireId);
        RepertoireLine line = lineRepo.findByIdAndRepertoireId(lineId, repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Line not found: " + lineId));

        if (request.lineName() != null) line.setLineName(request.lineName());
        if (request.pgn() != null) {
            if (!pgnParser.isValid(request.pgn())) {
                throw new IllegalArgumentException("Invalid PGN: " + request.pgn());
            }
            line.setPgn(request.pgn());
            line.getMoves().clear();
            List<LineMove> newMoves = pgnParser.parse(line, request.pgn());
            line.getMoves().addAll(newMoves);
        }
        if (request.notes() != null) line.setNotes(request.notes());
        if (request.drillPriority() != null) line.setDrillPriority(request.drillPriority());

        RepertoireLine saved = lineRepo.save(line);
        return toLineResponse(saved);
    }

    @Transactional
    public void deleteLine(User player, UUID repertoireId, UUID lineId) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);
        RepertoireLine line = lineRepo.findByIdAndRepertoireId(lineId, repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Line not found: " + lineId));
        repertoire.getLines().remove(line);
    }

    // ══════════════════════════════════════════════════════════
    //  MOVE ANNOTATION
    // ══════════════════════════════════════════════════════════

    @Transactional
    public MoveResponse updateMoveAnnotation(User player, UUID repertoireId, UUID lineId, UUID moveId,
                                              UpdateMoveAnnotationRequest request) {
        findRepertoireForPlayer(player, repertoireId);
        lineRepo.findByIdAndRepertoireId(lineId, repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Line not found: " + lineId));

        LineMove move = moveRepo.findById(moveId)
                .orElseThrow(() -> new EntityNotFoundException("Move not found: " + moveId));

        // Verify the move belongs to this line
        if (!move.getLine().getId().equals(lineId)) {
            throw new EntityNotFoundException("Move does not belong to this line");
        }

        move.setAnnotation(request.annotation());
        LineMove saved = moveRepo.save(move);
        return toMoveResponse(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  DRILL MODE
    // ══════════════════════════════════════════════════════════

    public DrillResponse getNextDrill(User player, UUID repertoireId) {
        findRepertoireForPlayer(player, repertoireId);

        List<RepertoireLine> candidates = lineRepo.findDrillCandidates(repertoireId);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No lines available to drill in this repertoire");
        }

        RepertoireLine selected = weightedSelect(candidates);

        List<LineMove> moves = selected.getMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException(
                    "Line '" + selected.getLineName() + "' has no parsed moves. "
                    + "Delete and re-add the line to generate moves.");
        }

        int maxIndex = moves.size() - 1;
        if (maxIndex < 1) {
            LineMove expectedNext = moves.get(0);
            return new DrillResponse(
                    selected.getId(),
                    selected.getLineName(),
                    selected.getRepertoire().getName(),
                    0,
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    expectedNext.getMoveSan(),
                    expectedNext.getMoveUci()
            );
        }

        int positionIndex = ThreadLocalRandom.current().nextInt(0, maxIndex);
        LineMove currentPosition = moves.get(positionIndex);
        LineMove expectedNext = moves.get(positionIndex + 1);

        return new DrillResponse(
                selected.getId(),
                selected.getLineName(),
                selected.getRepertoire().getName(),
                currentPosition.getMoveNumber(),
                currentPosition.getFenAfter(),
                expectedNext.getMoveSan(),
                expectedNext.getMoveUci()
        );
    }

    @Transactional
    public void recordDrillResult(User player, UUID repertoireId, DrillResultRequest request) {
        findRepertoireForPlayer(player, repertoireId);

        RepertoireLine line = lineRepo.findByIdAndRepertoireId(request.lineId(), repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Line not found: " + request.lineId()));

        line.recordDrill();

        if (request.correct()) {
            line.setDrillPriority(Math.max(1, line.getDrillPriority() - 1));
        } else {
            line.setDrillPriority(Math.min(10, line.getDrillPriority() + 2));
        }

        lineRepo.save(line);
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════

    private Repertoire findRepertoireForPlayer(User player, UUID repertoireId) {
        return repertoireRepo.findByIdAndPlayer(repertoireId, player)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));
    }

    private RepertoireLine weightedSelect(List<RepertoireLine> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(RepertoireLine::getDrillPriority)
                .sum();

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (RepertoireLine candidate : candidates) {
            cumulative += candidate.getDrillPriority();
            if (random < cumulative) {
                return candidate;
            }
        }

        return candidates.get(0);
    }

    // ── Mapping helpers ──────────────────────────────────────

    private RepertoireResponse toRepertoireResponse(Repertoire r) {
        return new RepertoireResponse(
                r.getId(),
                r.getName(),
                r.getColor(),
                r.getRootMove(),
                r.getDescription(),
                r.getLines().size(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private RepertoireDetailResponse toRepertoireDetailResponse(Repertoire r) {
        List<LineResponse> lineResponses = r.getLines().stream()
                .map(this::toLineResponse)
                .toList();

        return new RepertoireDetailResponse(
                r.getId(),
                r.getName(),
                r.getColor(),
                r.getRootMove(),
                r.getDescription(),
                lineResponses,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private LineResponse toLineResponse(RepertoireLine l) {
        OpeningSummary openingSummary = null;
        if (l.getOpening() != null) {
            Opening o = l.getOpening();
            openingSummary = new OpeningSummary(o.getId(), o.getEcoCode(), o.getName(), o.getVariation());
        }

        List<MoveResponse> moveResponses = l.getMoves().stream()
                .sorted(Comparator.comparingInt(LineMove::getMoveNumber))
                .map(this::toMoveResponse)
                .toList();

        return new LineResponse(
                l.getId(),
                l.getLineName(),
                l.getPgn(),
                l.getNotes(),
                l.getDrillPriority(),
                l.getTimesDrilled(),
                l.getMoves().size(),
                l.getLastDrilledAt(),
                openingSummary,
                moveResponses,
                l.getCreatedAt()
        );
    }

    private MoveResponse toMoveResponse(LineMove m) {
        return new MoveResponse(
                m.getId(),
                m.getMoveNumber(),
                m.getMoveSan(),
                m.getMoveUci(),
                m.getFenAfter(),
                m.getAnnotation()
        );
    }
}