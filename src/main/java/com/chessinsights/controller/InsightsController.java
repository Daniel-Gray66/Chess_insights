package com.chessinsights.controller;

import com.chessinsights.dto.InsightsDto;
import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.service.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Insights", description = "Chess performance analysis")
public class InsightsController {

    private final InsightsService insightsService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get personalized insights",
               description = "Analyzes your game history and returns performance rating, " +
                             "streaks, tilt detection, opening recommendations, " +
                             "playstyle analysis, and rival stats. " +
                             "Filter by time class, time period, and/or color.")
    public ResponseEntity<InsightsDto> getInsights(
            Authentication auth,
            @Parameter(description = "Filter by time class (rapid, blitz, bullet, daily)")
            @RequestParam(required = false) String timeClass,
            @Parameter(description = "Time period: 7d, 30d, 90d, 6m, 1y, all (default: all)")
            @RequestParam(required = false, defaultValue = "all") String period,
            @Parameter(description = "Filter by color (white, black)")
            @RequestParam(required = false) String color) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        InsightsDto insights = insightsService.generateInsights(user, timeClass, period, color);
        return ResponseEntity.ok(insights);
    }
}