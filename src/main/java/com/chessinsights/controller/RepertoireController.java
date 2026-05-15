package com.chessinsights.controller;

import com.chessinsights.dto.RepertoireDtos.*;
import com.chessinsights.entity.Repertoire;
import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.config.CurrentUserProvider;
import com.chessinsights.service.DeviationAnalysisService;
import com.chessinsights.service.RepertoireService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repertoires")
public class RepertoireController {

    private final RepertoireService repertoireService;
    private final DeviationAnalysisService deviationService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public RepertoireController(RepertoireService repertoireService,
                                 CurrentUserProvider currentUserProvider,
                                 DeviationAnalysisService deviationService,
                                 UserRepository userRepository) {
        this.repertoireService = repertoireService;
        this.deviationService = deviationService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    private User getUser(Authentication auth) {
        return currentUserProvider.getUser(auth);
    }

    // ══════════════════════════════════════════════════════════
    //  REPERTOIRE CRUD
    // ══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<List<RepertoireResponse>> listRepertoires(
            Authentication auth,
            @RequestParam(required = false) Repertoire.Color color) {
        User player = getUser(auth);
        return ResponseEntity.ok(repertoireService.listRepertoires(player, color));
    }

    @PostMapping
    public ResponseEntity<RepertoireResponse> createRepertoire(
            Authentication auth,
            @RequestBody CreateRepertoireRequest request) {
        User player = getUser(auth);
        RepertoireResponse created = repertoireService.createRepertoire(player, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepertoireDetailResponse> getRepertoire(
            Authentication auth,
            @PathVariable UUID id) {
        User player = getUser(auth);
        return ResponseEntity.ok(repertoireService.getRepertoireDetail(player, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepertoire(
            Authentication auth,
            @PathVariable UUID id) {
        User player = getUser(auth);
        repertoireService.deleteRepertoire(player, id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════
    //  LINE CRUD
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{id}/lines")
    public ResponseEntity<LineResponse> addLine(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody AddLineRequest request) {
        User player = getUser(auth);
        LineResponse created = repertoireService.addLine(player, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{repertoireId}/lines/{lineId}")
    public ResponseEntity<LineResponse> updateLine(
            Authentication auth,
            @PathVariable UUID repertoireId,
            @PathVariable UUID lineId,
            @RequestBody UpdateLineRequest request) {
        User player = getUser(auth);
        return ResponseEntity.ok(repertoireService.updateLine(player, repertoireId, lineId, request));
    }

    @DeleteMapping("/{repertoireId}/lines/{lineId}")
    public ResponseEntity<Void> deleteLine(
            Authentication auth,
            @PathVariable UUID repertoireId,
            @PathVariable UUID lineId) {
        User player = getUser(auth);
        repertoireService.deleteLine(player, repertoireId, lineId);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════
    //  MOVE ANNOTATION
    // ══════════════════════════════════════════════════════════

    @PutMapping("/{repertoireId}/lines/{lineId}/moves/{moveId}")
    public ResponseEntity<MoveResponse> updateMoveAnnotation(
            Authentication auth,
            @PathVariable UUID repertoireId,
            @PathVariable UUID lineId,
            @PathVariable UUID moveId,
            @RequestBody UpdateMoveAnnotationRequest request) {
        User player = getUser(auth);
        return ResponseEntity.ok(
                repertoireService.updateMoveAnnotation(player, repertoireId, lineId, moveId, request));
    }

    // ══════════════════════════════════════════════════════════
    //  DRILL MODE
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}/drill")
    public ResponseEntity<DrillResponse> getDrill(
            Authentication auth,
            @PathVariable UUID id) {
        User player = getUser(auth);
        return ResponseEntity.ok(repertoireService.getNextDrill(player, id));
    }

    @PostMapping("/{id}/drill/result")
    public ResponseEntity<Void> submitDrillResult(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody DrillResultRequest request) {
        User player = getUser(auth);
        repertoireService.recordDrillResult(player, id, request);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════
    //  ANALYTICS — prep vs actual play
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{id}/deviations")
    public ResponseEntity<List<DeviationResponse>> getDeviations(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        User player = getUser(auth);
        return ResponseEntity.ok(deviationService.findDeviations(player, id, limit));
    }

    @GetMapping("/{id}/accuracy")
    public ResponseEntity<AccuracyResponse> getAccuracy(
            Authentication auth,
            @PathVariable UUID id) {
        User player = getUser(auth);
        return ResponseEntity.ok(deviationService.calculateAccuracy(player, id));
    }
}