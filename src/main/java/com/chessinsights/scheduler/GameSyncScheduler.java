package com.chessinsights.scheduler;

import com.chessinsights.entity.User;
import com.chessinsights.repository.UserRepository;
import com.chessinsights.service.GameSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task that syncs games for all users nightly.
 * Cron expression is configurable via app.sync.cron in application.properties.
 * Default: every day at 3:00 AM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameSyncScheduler {

    private final UserRepository userRepository;
    private final GameSyncService gameSyncService;

    @Scheduled(cron = "${app.sync.cron}")
    public void syncAllUsers() {
        log.info("Starting scheduled game sync for all users...");

        List<User> users = userRepository.findAll();

        for (User user : users) {
            try {
                log.info("Syncing games for user: {}", user.getChessComUsername());
                gameSyncService.syncGames(user);
            } catch (Exception e) {
                log.error("Scheduled sync failed for '{}': {}",
                        user.getChessComUsername(), e.getMessage());
            }
        }

        log.info("Scheduled sync completed for {} users", users.size());
    }
}