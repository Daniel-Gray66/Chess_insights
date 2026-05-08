package com.chessinsights.dto;

import com.chessinsights.entity.Repertoire;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RepertoireDtos {

    // ── Request DTOs ─────────────────────────────────────────

    public record CreateRepertoireRequest(
        String name,
        Repertoire.Color color,
        String rootMove,
        String description
    ) {}

    public record AddLineRequest(
        String lineName,
        String pgn,
        String notes,
        Integer drillPriority,
        UUID openingId
    ) {}

    public record UpdateLineRequest(
        String lineName,
        String pgn,
        String notes,
        Integer drillPriority
    ) {}

    // ── Response DTOs ────────────────────────────────────────

    public record RepertoireResponse(
        UUID id,
        String name,
        Repertoire.Color color,
        String rootMove,
        String description,
        int lineCount,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record RepertoireDetailResponse(
        UUID id,
        String name,
        Repertoire.Color color,
        String rootMove,
        String description,
        List<LineResponse> lines,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record LineResponse(
        UUID id,
        String lineName,
        String pgn,
        String notes,
        int drillPriority,
        int timesDrilled,
        int moveCount,
        Instant lastDrilledAt,
        OpeningSummary opening,
        Instant createdAt
    ) {}

    public record OpeningSummary(
        UUID id,
        String ecoCode,
        String name,
        String variation
    ) {}

    public record DrillResponse(
        UUID lineId,
        String lineName,
        String repertoireName,
        int moveNumber,
        String fenAtPosition,
        String expectedMoveSan,
        String expectedMoveUci
    ) {}

    public record DrillResultRequest(
        UUID lineId,
        boolean correct
    ) {}

    // ── Deviation & Accuracy ─────────────────────────────────

    public record DeviationResponse(
        Long gameId,
        String opponent,
        Instant playedAt,
        String repertoireLineName,
        int deviationAtMove,
        String expectedMove,
        String actualMove,
        String result
    ) {}

    public record AccuracyResponse(
        double overallAccuracy,
        int movesMatchedPrep,
        int totalPrepMoves,
        List<LineAccuracy> perLineBreakdown
    ) {}

    public record LineAccuracy(
        String lineName,
        double accuracy,
        int matchedMoves,
        int totalMoves
    ) {}
}