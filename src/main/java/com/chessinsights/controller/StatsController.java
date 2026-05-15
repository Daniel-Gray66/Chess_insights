package com.chessinsights.controller;

import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.GameRepository;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.config.CurrentUserProvider;
import com.chessinsights.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics", description = "Chess performance analytics and insights")
public class StatsController {

    private final StatsService statsService;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/overview")
    @Operation(summary = "Overall stats overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            Authentication auth,
            @RequestParam(required = false) String timeClass,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String range) {
        User user = getUser(auth);
        Instant from = parseRange(range);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("chessComUsername", user.getChessComUsername());
        overview.put("totalGames", statsService.countGames(user, timeClass, color, from));
        overview.put("lastSyncedAt", user.getLastSyncedAt());
        overview.put("overall", statsService.getOverallRecord(user, timeClass, color, from));
        overview.put("avgAccuracy", statsService.getAverageAccuracy(user, timeClass, color, from));
        overview.put("avgOpponentRating", statsService.getAverageOpponentRating(user, timeClass, color, from));

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/record")
    @Operation(summary = "Win/loss/draw record")
    public ResponseEntity<Map<String, Object>> getRecord(
            Authentication auth,
            @RequestParam(required = false) String timeClass,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String range) {
        User user = getUser(auth);
        Instant from = parseRange(range);
        return ResponseEntity.ok(statsService.getOverallRecord(user, timeClass, color, from));
    }

    @GetMapping("/color")
    @Operation(summary = "Performance by color")
    public ResponseEntity<Map<String, Object>> getColorBreakdown(
            Authentication auth,
            @RequestParam(required = false) String timeClass,
            @RequestParam(required = false) String range) {
        User user = getUser(auth);
        Instant from = parseRange(range);
        return ResponseEntity.ok(statsService.getColorBreakdown(user, timeClass, from));
    }

    @GetMapping("/openings")
    @Operation(summary = "Opening analysis")
    public ResponseEntity<List<Map<String, Object>>> getOpeningStats(
            Authentication auth,
            @RequestParam(required = false) String timeClass,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String range) {
        User user = getUser(auth);
        Instant from = parseRange(range);
        return ResponseEntity.ok(statsService.getOpeningStats(user, timeClass, color, from));
    }

    @GetMapping("/rating")
    @Operation(summary = "Rating progression over time")
    public ResponseEntity<List<Map<String, Object>>> getRatingProgression(
            Authentication auth,
            @RequestParam(required = false) String timeClass,
            @RequestParam(required = false) String range) {
        User user = getUser(auth);
        Instant from = parseRange(range);
        return ResponseEntity.ok(statsService.getRatingProgression(user, timeClass, from));
    }

    @GetMapping("/time-of-day")
    @Operation(summary = "Performance by hour of day (UTC)")
    public ResponseEntity<List<Map<String, Object>>> getTimeOfDayStats(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getTimeOfDayStats(user));
    }

    @GetMapping("/day-of-week")
    @Operation(summary = "Performance by day of week")
    public ResponseEntity<List<Map<String, Object>>> getDayOfWeekStats(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getDayOfWeekStats(user));
    }

    private User getUser(Authentication auth) {
        return currentUserProvider.getUser(auth);
    }

    private Instant parseRange(String range) {
        if (range == null || range.equalsIgnoreCase("all")) return null;
        Instant now = Instant.now();
        return switch (range.toLowerCase()) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            case "1y" -> now.minus(365, ChronoUnit.DAYS);
            default -> null;
        };
    }
}