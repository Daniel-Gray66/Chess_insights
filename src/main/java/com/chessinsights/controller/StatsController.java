package com.chessinsights.controller;

import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.GameRepository;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/overview")
    @Operation(summary = "Overall stats overview",
               description = "Win/loss/draw record, total games, and last sync time.")
    public ResponseEntity<Map<String, Object>> getOverview(Authentication auth) {
        User user = getUser(auth);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("chessComUsername", user.getChessComUsername());
        overview.put("totalGames", gameRepository.countByUser(user));
        overview.put("lastSyncedAt", user.getLastSyncedAt());
        overview.put("overall", statsService.getOverallRecord(user, null));

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/record")
    @Operation(summary = "Win/loss/draw record",
               description = "Optionally filter by time class: rapid, blitz, bullet, daily.")
    public ResponseEntity<Map<String, Object>> getRecord(
            Authentication auth,
            @Parameter(description = "Filter by time class (rapid, blitz, bullet, daily)")
            @RequestParam(required = false) String timeClass) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getOverallRecord(user, timeClass));
    }

    @GetMapping("/color")
    @Operation(summary = "Performance by color",
               description = "Win/loss/draw breakdown playing as white vs black.")
    public ResponseEntity<Map<String, Object>> getColorBreakdown(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getColorBreakdown(user));
    }

    @GetMapping("/openings")
    @Operation(summary = "Opening analysis",
               description = "Win rate by opening, sorted by most played. " +
                             "Shows ECO code, win/loss/draw counts, and win percentage.")
    public ResponseEntity<List<Map<String, Object>>> getOpeningStats(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getOpeningStats(user));
    }

    @GetMapping("/rating")
    @Operation(summary = "Rating progression over time",
               description = "Returns rating data points ordered chronologically. " +
                             "Optionally filter by time class.")
    public ResponseEntity<List<Map<String, Object>>> getRatingProgression(
            Authentication auth,
            @Parameter(description = "Filter by time class (rapid, blitz, bullet, daily)")
            @RequestParam(required = false) String timeClass) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getRatingProgression(user, timeClass));
    }

    @GetMapping("/time-of-day")
    @Operation(summary = "Performance by hour of day (UTC)",
               description = "Win rate for each hour of the day. " +
                             "Discover when you play your best chess.")
    public ResponseEntity<List<Map<String, Object>>> getTimeOfDayStats(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getTimeOfDayStats(user));
    }

    @GetMapping("/day-of-week")
    @Operation(summary = "Performance by day of week",
               description = "Win rate for each day of the week. " +
                             "Find out if you play better on weekends or weekdays.")
    public ResponseEntity<List<Map<String, Object>>> getDayOfWeekStats(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(statsService.getDayOfWeekStats(user));
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}