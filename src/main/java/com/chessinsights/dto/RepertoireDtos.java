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

    public record UpdateMoveAnnotationRequest(
            String annotation
    ) {}

    public record UpdateVisibilityRequest(
            String visibility
    ) {}

    // ── Response DTOs ────────────────────────────────────────

    public record AuthorInfo(
            Long id,
            String username,
            String chessComUsername
    ) {}

    public record RepertoireResponse(
            UUID id,
            String name,
            Repertoire.Color color,
            String rootMove,
            String description,
            int lineCount,
            String visibility,
            AuthorInfo author,
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
            String visibility,
            AuthorInfo author,
            boolean isOwner,
            boolean isBookmarked,
            long bookmarkCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CommunityRepertoireResponse(
            UUID id,
            String name,
            Repertoire.Color color,
            String rootMove,
            String description,
            int lineCount,
            AuthorInfo author,
            long bookmarkCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record BookmarkResponse(
            UUID repertoireId,
            String repertoireName,
            Repertoire.Color color,
            String rootMove,
            String description,
            AuthorInfo author,
            int lineCount,
            Instant bookmarkedAt
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
            List<MoveResponse> moves,
            Instant createdAt
    ) {}

    public record MoveResponse(
            UUID id,
            int moveNumber,
            String moveSan,
            String moveUci,
            String fenAfter,
            String annotation
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