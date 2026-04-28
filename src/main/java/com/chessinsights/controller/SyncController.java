package com.chessinsights.controller;

import com.chessinsights.entity.SyncJob;
import com.chessinsights.entity.User;
import com.chessinsights.exception.ResourceNotFoundException;
import com.chessinsights.repository.SyncJobRepository;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.service.GameSyncService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Game Sync", description = "Sync your Chess.com games into the database")
public class SyncController {

    private final GameSyncService gameSyncService;
    private final SyncJobRepository syncJobRepository;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Trigger a game sync",
               description = "Pulls all your games from Chess.com. First sync may take a few " +
                             "minutes depending on how many games you have. Subsequent syncs " +
                             "will skip games already in the database.")
    public ResponseEntity<Map<String, Object>> triggerSync(Authentication auth) {
        User user = getUser(auth);

        SyncJob job = gameSyncService.syncGames(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("syncId", job.getId());
        response.put("status", job.getStatus());
        response.put("gamesFetched", job.getGamesFetched());
        response.put("newGamesSaved", job.getNewGamesSaved());
        response.put("archivesProcessed", job.getArchivesProcessed());
        response.put("startedAt", job.getStartedAt());
        response.put("completedAt", job.getCompletedAt());

        if (job.getErrorMessage() != null) {
            response.put("error", job.getErrorMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "View past sync jobs",
               description = "Returns a list of all sync jobs for the authenticated user, " +
                             "ordered by most recent first.")
    public ResponseEntity<List<Map<String, Object>>> getSyncHistory(Authentication auth) {
        User user = getUser(auth);
        List<SyncJob> jobs = syncJobRepository.findByUserOrderByStartedAtDesc(user);

        List<Map<String, Object>> response = jobs.stream().map(job -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("syncId", job.getId());
            m.put("status", job.getStatus());
            m.put("gamesFetched", job.getGamesFetched());
            m.put("newGamesSaved", job.getNewGamesSaved());
            m.put("archivesProcessed", job.getArchivesProcessed());
            m.put("startedAt", job.getStartedAt());
            m.put("completedAt", job.getCompletedAt());
            if (job.getErrorMessage() != null) {
                m.put("error", job.getErrorMessage());
            }
            return m;
        }).toList();

        return ResponseEntity.ok(response);
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}