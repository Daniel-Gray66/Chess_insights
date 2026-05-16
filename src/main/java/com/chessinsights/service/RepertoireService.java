package com.chessinsights.service;

import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.*;
import com.chessinsights.repository.*;
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
    private final RepertoireBookmarkRepository bookmarkRepo;
    private final PgnParserService pgnParser;

    public RepertoireService(RepertoireRepository repertoireRepo,
                             RepertoireLineRepository lineRepo,
                             LineMoveRepository moveRepo,
                             OpeningRepository openingRepo,
                             RepertoireBookmarkRepository bookmarkRepo,
                             PgnParserService pgnParser) {
        this.repertoireRepo = repertoireRepo;
        this.lineRepo = lineRepo;
        this.moveRepo = moveRepo;
        this.openingRepo = openingRepo;
        this.bookmarkRepo = bookmarkRepo;
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
        boolean isBookmarked = bookmarkRepo.existsByUserAndRepertoire(player, repertoire);
        long bookmarkCount = bookmarkRepo.countByRepertoire(repertoire);
        return toRepertoireDetailResponse(repertoire, true, isBookmarked, bookmarkCount);
    }

    @Transactional
    public void deleteRepertoire(User player, UUID repertoireId) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);
        repertoireRepo.delete(repertoire);
    }

    // ══════════════════════════════════════════════════════════
    //  VISIBILITY
    // ══════════════════════════════════════════════════════════

    @Transactional
    public RepertoireResponse updateVisibility(User player, UUID repertoireId, String visibility) {
        Repertoire repertoire = findRepertoireForPlayer(player, repertoireId);
        try {
            repertoire.setVisibility(Repertoire.Visibility.valueOf(visibility.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid visibility: " + visibility + ". Must be PRIVATE, PUBLIC, or SHARED.");
        }
        Repertoire saved = repertoireRepo.save(repertoire);
        return toRepertoireResponse(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  COMMUNITY — browse, search, view
    // ══════════════════════════════════════════════════════════

    public List<CommunityRepertoireResponse> browsePublic(Repertoire.Color color) {
        List<Repertoire> repertoires = (color != null)
                ? repertoireRepo.findPublicRepertoiresByColor(color)
                : repertoireRepo.findPublicRepertoires();

        return repertoires.stream()
                .map(this::toCommunityResponse)
                .toList();
    }

    public List<CommunityRepertoireResponse> searchPublic(String query, Repertoire.Color color) {
        List<Repertoire> repertoires = (color != null)
                ? repertoireRepo.searchPublicRepertoiresByColor(query, color)
                : repertoireRepo.searchPublicRepertoires(query);

        return repertoires.stream()
                .map(this::toCommunityResponse)
                .toList();
    }

    public RepertoireDetailResponse getPublicRepertoireDetail(UUID repertoireId, User viewer) {
        Repertoire repertoire = repertoireRepo.findById(repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        if (repertoire.getVisibility() != Repertoire.Visibility.PUBLIC) {
            // If not public, only the owner can see it
            if (viewer == null || !repertoire.getPlayer().getId().equals(viewer.getId())) {
                throw new EntityNotFoundException("Repertoire not found: " + repertoireId);
            }
        }

        boolean isOwner = viewer != null && repertoire.getPlayer().getId().equals(viewer.getId());
        boolean isBookmarked = viewer != null && bookmarkRepo.existsByUserAndRepertoire(viewer, repertoire);
        long bookmarkCount = bookmarkRepo.countByRepertoire(repertoire);

        return toRepertoireDetailResponse(repertoire, isOwner, isBookmarked, bookmarkCount);
    }

    // ══════════════════════════════════════════════════════════
    //  BOOKMARKS
    // ══════════════════════════════════════════════════════════

    @Transactional
    public void bookmarkRepertoire(User user, UUID repertoireId) {
        Repertoire repertoire = repertoireRepo.findById(repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        if (repertoire.getVisibility() != Repertoire.Visibility.PUBLIC) {
            throw new IllegalArgumentException("Can only bookmark public repertoires");
        }

        if (repertoire.getPlayer().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Cannot bookmark your own repertoire");
        }

        if (!bookmarkRepo.existsByUserAndRepertoire(user, repertoire)) {
            bookmarkRepo.save(new RepertoireBookmark(user, repertoire));
        }
    }

    @Transactional
    public void unbookmarkRepertoire(User user, UUID repertoireId) {
        Repertoire repertoire = repertoireRepo.findById(repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        bookmarkRepo.deleteByUserAndRepertoire(user, repertoire);
    }

    public List<BookmarkResponse> getBookmarks(User user) {
        return bookmarkRepo.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toBookmarkResponse)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  COPY REPERTOIRE
    // ══════════════════════════════════════════════════════════

    @Transactional
    public RepertoireResponse copyRepertoire(User user, UUID repertoireId) {
        Repertoire source = repertoireRepo.findById(repertoireId)
                .orElseThrow(() -> new EntityNotFoundException("Repertoire not found: " + repertoireId));

        if (source.getVisibility() != Repertoire.Visibility.PUBLIC) {
            throw new IllegalArgumentException("Can only copy public repertoires");
        }

        // Create the copy
        Repertoire copy = new Repertoire(
                user,
                source.getName() + " (copy)",
                source.getColor(),
                source.getRootMove(),
                source.getDescription()
        );
        copy.setVisibility(Repertoire.Visibility.PRIVATE);
        Repertoire savedCopy = repertoireRepo.save(copy);

        // Copy all lines and moves
        for (RepertoireLine sourceLine : source.getLines()) {
            RepertoireLine lineCopy = new RepertoireLine(
                    savedCopy,
                    sourceLine.getLineName(),
                    sourceLine.getPgn(),
                    sourceLine.getNotes()
            );
            lineCopy.setDrillPriority(sourceLine.getDrillPriority());
            if (sourceLine.getOpening() != null) {
                lineCopy.setOpening(sourceLine.getOpening());
            }

            // Copy moves
            for (LineMove sourceMove : sourceLine.getMoves()) {
                LineMove moveCopy = new LineMove();
                moveCopy.setLine(lineCopy);
                moveCopy.setMoveNumber(sourceMove.getMoveNumber());
                moveCopy.setMoveSan(sourceMove.getMoveSan());
                moveCopy.setMoveUci(sourceMove.getMoveUci());
                moveCopy.setFenAfter(sourceMove.getFenAfter());
                moveCopy.setAnnotation(sourceMove.getAnnotation());
                lineCopy.getMoves().add(moveCopy);
            }

            savedCopy.getLines().add(lineCopy);
        }

        repertoireRepo.save(savedCopy);
        return toRepertoireResponse(savedCopy);
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

    private AuthorInfo toAuthorInfo(User player) {
        return new AuthorInfo(player.getId(), player.getUsername(), player.getChessComUsername());
    }

    private RepertoireResponse toRepertoireResponse(Repertoire r) {
        return new RepertoireResponse(
                r.getId(),
                r.getName(),
                r.getColor(),
                r.getRootMove(),
                r.getDescription(),
                r.getLines().size(),
                r.getVisibility().name(),
                toAuthorInfo(r.getPlayer()),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private RepertoireDetailResponse toRepertoireDetailResponse(Repertoire r, boolean isOwner,
                                                                  boolean isBookmarked, long bookmarkCount) {
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
                r.getVisibility().name(),
                toAuthorInfo(r.getPlayer()),
                isOwner,
                isBookmarked,
                bookmarkCount,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private CommunityRepertoireResponse toCommunityResponse(Repertoire r) {
        return new CommunityRepertoireResponse(
                r.getId(),
                r.getName(),
                r.getColor(),
                r.getRootMove(),
                r.getDescription(),
                r.getLines().size(),
                toAuthorInfo(r.getPlayer()),
                bookmarkRepo.countByRepertoire(r),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private BookmarkResponse toBookmarkResponse(RepertoireBookmark b) {
        Repertoire r = b.getRepertoire();
        return new BookmarkResponse(
                r.getId(),
                r.getName(),
                r.getColor(),
                r.getRootMove(),
                r.getDescription(),
                toAuthorInfo(r.getPlayer()),
                r.getLines().size(),
                b.getCreatedAt()
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