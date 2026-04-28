package com.chessinsights.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Client for the Chess.com Published Data API.
 *
 * API Docs: https://www.chess.com/announcements/view/published-data-api
 *
 * Key endpoints used:
 *  - GET /pub/player/{username}                    → player profile
 *  - GET /pub/player/{username}/stats              → rating stats
 *  - GET /pub/player/{username}/games/archives     → list of monthly archive URLs
 *  - GET /pub/player/{username}/games/{YYYY}/{MM}  → games for a given month
 *
 * Rate limits: serial requests are unlimited. Parallel requests may get 429s.
 * We make requests sequentially and include a User-Agent with contact info as recommended.
 */
@Slf4j
@Component
public class ChessComClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ChessComClient(
            @Value("${app.chesscom.base-url}") String baseUrl,
            @Value("${app.chesscom.user-agent}") String userAgent,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Verify a Chess.com username exists.
     * GET /pub/player/{username}
     */
    public boolean playerExists(String username) {
        try {
            restClient.get()
                    .uri("/player/{username}", username.toLowerCase())
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (RestClientException e) {
            log.warn("Chess.com player lookup failed for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Get list of monthly archive URLs for a player.
     * GET /pub/player/{username}/games/archives
     *
     * Returns URLs like: ["https://api.chess.com/pub/player/user/games/2024/01", ...]
     */
    public List<String> getArchives(String username) {
        try {
            String response = restClient.get()
                    .uri("/player/{username}/games/archives", username.toLowerCase())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode archivesNode = root.get("archives");

            List<String> archives = new ArrayList<>();
            if (archivesNode != null && archivesNode.isArray()) {
                for (JsonNode node : archivesNode) {
                    archives.add(node.asText());
                }
            }
            return archives;
        } catch (Exception e) {
            log.error("Failed to fetch archives for '{}': {}", username, e.getMessage());
            throw new RuntimeException("Failed to fetch archives from Chess.com", e);
        }
    }

    /**
     * Get all games for a specific monthly archive.
     * GET /pub/player/{username}/games/{YYYY}/{MM}
     *
     * Returns the raw JSON response containing a "games" array.
     * Each game object contains: url, pgn, time_control, time_class,
     * white/black player info (username, rating, result), etc.
     */
    public JsonNode getGamesForMonth(String archiveUrl) {
        try {
            String response = restClient.get()
                    .uri(archiveUrl)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to fetch games from '{}': {}", archiveUrl, e.getMessage());
            throw new RuntimeException("Failed to fetch games from Chess.com", e);
        }
    }

    /**
     * Get player stats (ratings for each time control).
     * GET /pub/player/{username}/stats
     */
    public JsonNode getPlayerStats(String username) {
        try {
            String response = restClient.get()
                    .uri("/player/{username}/stats", username.toLowerCase())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to fetch stats for '{}': {}", username, e.getMessage());
            throw new RuntimeException("Failed to fetch player stats from Chess.com", e);
        }
    }
}
