package com.chessinsights.controller;

import com.chessinsights.entity.ChessGame;
import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.GameRepository;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.config.CurrentUserProvider;
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
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Games", description = "Browse and search your chess games")
public class GameController {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "Search and filter games")
    public ResponseEntity<List<Map<String, Object>>> searchGames(
            Authentication auth,
            @Parameter(description = "Filter by time class") @RequestParam(required = false) String timeClass,
            @Parameter(description = "Filter by result (win, loss, draw)") @RequestParam(required = false) String result,
            @Parameter(description = "Search by opponent username") @RequestParam(required = false) String opponent,
            @Parameter(description = "Search by opening name") @RequestParam(required = false) String opening
    ) {
        User user = getUser(auth);

        // Use simple JPA queries, then filter in Java to avoid PostgreSQL null parameter type issues
        List<ChessGame> games;

        if (timeClass != null && !timeClass.isBlank()) {
            games = gameRepository.findByUserAndTimeClassOrderByPlayedAtDesc(user, timeClass);
        } else {
            games = gameRepository.findByUserOrderByPlayedAtDesc(user);
        }

        // Apply additional filters in Java
        Stream<ChessGame> stream = games.stream();

        if (result != null && !result.isBlank()) {
            stream = stream.filter(g -> result.equalsIgnoreCase(g.getResult()));
        }
        if (opponent != null && !opponent.isBlank()) {
            String lowerOpp = opponent.toLowerCase();
            stream = stream.filter(g -> g.getOpponentUsername() != null &&
                    g.getOpponentUsername().toLowerCase().contains(lowerOpp));
        }
        if (opening != null && !opening.isBlank()) {
            String lowerOpening = opening.toLowerCase();
            stream = stream.filter(g -> g.getOpeningName() != null &&
                    g.getOpeningName().toLowerCase().contains(lowerOpening));
        }

        List<Map<String, Object>> response = stream
                .map(this::mapGameToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific game by ID")
    public ResponseEntity<Map<String, Object>> getGame(
            Authentication auth,
            @PathVariable Long id) {
        User user = getUser(auth);

        ChessGame game = gameRepository.findById(id)
                .filter(g -> g.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));

        Map<String, Object> response = mapGameToResponse(game);
        response.put("pgn", game.getPgn());

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> mapGameToResponse(ChessGame game) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", game.getId());
        m.put("playedAt", game.getPlayedAt());
        m.put("timeClass", game.getTimeClass());
        m.put("timeControl", game.getTimeControl());
        m.put("userColor", game.getUserColor());
        m.put("userRating", game.getUserRating());
        m.put("opponentUsername", game.getOpponentUsername());
        m.put("opponentRating", game.getOpponentRating());
        m.put("result", game.getResult());
        m.put("termination", game.getTermination());
        m.put("opening", game.getOpeningName());
        m.put("ecoCode", game.getEcoCode());
        m.put("numMoves", game.getNumMoves());
        m.put("accuracy", game.getAccuracy());
        m.put("gameUrl", game.getGameUrl());
        return m;
    }

    private User getUser(Authentication auth) {
        return currentUserProvider.getUser(auth);
    }
}