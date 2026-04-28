package com.chessinsights.service;

import com.chessinsights.entity.User;
import com.chessinsights.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes chess analytics from stored game data.
 * Results are cached in Redis and invalidated on new game sync.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final GameRepository gameRepository;

    /**
     * Overall win/loss/draw record.
     * Returns: { "total": 500, "wins": 250, "losses": 200, "draws": 50,
     *            "winRate": 50.0, "lossRate": 40.0, "drawRate": 10.0 }
     */
    public Map<String, Object> getOverallRecord(User user, String timeClass) {
        List<Object[]> results;
        if (timeClass != null) {
            results = gameRepository.countByResultAndTimeClass(user, timeClass);
        } else {
            results = gameRepository.countByResult(user);
        }

        long wins = 0, losses = 0, draws = 0;
        for (Object[] row : results) {
            String result = (String) row[0];
            long count = (Long) row[1];
            switch (result) {
                case "win" -> wins = count;
                case "loss" -> losses = count;
                case "draw" -> draws = count;
            }
        }

        long total = wins + losses + draws;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("total", total);
        record.put("wins", wins);
        record.put("losses", losses);
        record.put("draws", draws);
        record.put("winRate", total > 0 ? round((double) wins / total * 100) : 0);
        record.put("lossRate", total > 0 ? round((double) losses / total * 100) : 0);
        record.put("drawRate", total > 0 ? round((double) draws / total * 100) : 0);

        if (timeClass != null) {
            record.put("timeClass", timeClass);
        }

        return record;
    }

    /**
     * Win/loss/draw breakdown by color (white vs black).
     */
    public Map<String, Object> getColorBreakdown(User user) {
        List<Object[]> results = gameRepository.countByColorAndResult(user);

        Map<String, Map<String, Long>> colorStats = new LinkedHashMap<>();
        colorStats.put("white", new LinkedHashMap<>(Map.of("win", 0L, "loss", 0L, "draw", 0L)));
        colorStats.put("black", new LinkedHashMap<>(Map.of("win", 0L, "loss", 0L, "draw", 0L)));

        for (Object[] row : results) {
            String color = (String) row[0];
            String result = (String) row[1];
            long count = (Long) row[2];
            if (colorStats.containsKey(color)) {
                colorStats.get(color).put(result, count);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        for (var entry : colorStats.entrySet()) {
            Map<String, Long> stats = entry.getValue();
            long total = stats.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> colorData = new LinkedHashMap<>(stats);
            colorData.put("total", total);
            colorData.put("winRate", total > 0 ? round((double) stats.get("win") / total * 100) : 0);
            response.put(entry.getKey(), colorData);
        }

        return response;
    }

    /**
     * Opening analysis - win rate by opening name.
     * Returns list sorted by total games played (most popular first).
     */
    public List<Map<String, Object>> getOpeningStats(User user) {
        List<Object[]> results = gameRepository.countByOpeningAndResult(user);

        // Group by opening name
        Map<String, Map<String, Object>> openingMap = new LinkedHashMap<>();
        for (Object[] row : results) {
            String openingName = (String) row[0];
            String ecoCode = (String) row[1];
            String result = (String) row[2];
            long count = (Long) row[3];

            openingMap.computeIfAbsent(openingName, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("opening", openingName);
                m.put("ecoCode", ecoCode);
                m.put("wins", 0L);
                m.put("losses", 0L);
                m.put("draws", 0L);
                return m;
            });

            Map<String, Object> stats = openingMap.get(openingName);
            switch (result) {
                case "win" -> stats.put("wins", count);
                case "loss" -> stats.put("losses", count);
                case "draw" -> stats.put("draws", count);
            }
        }

        // Calculate totals and win rates, sort by total games
        return openingMap.values().stream()
                .map(stats -> {
                    long w = (Long) stats.get("wins");
                    long l = (Long) stats.get("losses");
                    long d = (Long) stats.get("draws");
                    long total = w + l + d;
                    stats.put("total", total);
                    stats.put("winRate", total > 0 ? round((double) w / total * 100) : 0);
                    return stats;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("total"), (Long) a.get("total")))
                .collect(Collectors.toList());
    }

    /**
     * Rating progression over time.
     * Returns list of { "playedAt": "...", "rating": 1200, "timeClass": "blitz" }
     */
    public List<Map<String, Object>> getRatingProgression(User user, String timeClass) {
        List<Object[]> results;
        if (timeClass != null) {
            results = gameRepository.getRatingProgressionByTimeClass(user, timeClass);
            return results.stream().map(row -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("playedAt", row[0].toString());
                point.put("rating", row[1]);
                point.put("timeClass", timeClass);
                return point;
            }).collect(Collectors.toList());
        } else {
            results = gameRepository.getRatingProgression(user);
            return results.stream().map(row -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("playedAt", row[0].toString());
                point.put("rating", row[1]);
                point.put("timeClass", row[2]);
                return point;
            }).collect(Collectors.toList());
        }
    }

    /**
     * Performance by hour of day (UTC).
     * Helps answer: "When do I play my best chess?"
     */
    public List<Map<String, Object>> getTimeOfDayStats(User user) {
        List<Object[]> results = gameRepository.countByHourOfDayAndResult(user.getId());
        return aggregateTimeStats(results, "hour", 24);
    }

    /**
     * Performance by day of week.
     * Postgres DOW: 0=Sunday, 1=Monday, ..., 6=Saturday
     */
    public List<Map<String, Object>> getDayOfWeekStats(User user) {
        List<Object[]> results = gameRepository.countByDayOfWeekAndResult(user.getId());
        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday",
                             "Thursday", "Friday", "Saturday"};

        List<Map<String, Object>> stats = aggregateTimeStats(results, "dayIndex", 7);
        for (Map<String, Object> entry : stats) {
            int idx = ((Number) entry.get("dayIndex")).intValue();
            entry.put("day", dayNames[idx]);
        }
        return stats;
    }

    // --- Helper Methods ---

    private List<Map<String, Object>> aggregateTimeStats(List<Object[]> results,
                                                          String periodKey, int periods) {
        // Initialize all periods
        Map<Integer, Map<String, Long>> periodMap = new LinkedHashMap<>();
        for (int i = 0; i < periods; i++) {
            periodMap.put(i, new LinkedHashMap<>(Map.of("win", 0L, "loss", 0L, "draw", 0L)));
        }

        for (Object[] row : results) {
            int period = ((Number) row[0]).intValue();
            String result = (String) row[1];
            long count = ((Number) row[2]).longValue();
            if (periodMap.containsKey(period) && periodMap.get(period).containsKey(result)) {
                periodMap.get(period).put(result, count);
            }
        }

        List<Map<String, Object>> statsList = new ArrayList<>();
        for (var entry : periodMap.entrySet()) {
            Map<String, Long> counts = entry.getValue();
            long total = counts.values().stream().mapToLong(Long::longValue).sum();

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put(periodKey, entry.getKey());
            stat.putAll(counts);
            stat.put("total", total);
            stat.put("winRate", total > 0 ? round((double) counts.get("win") / total * 100) : 0);
            statsList.add(stat);
        }
        return statsList;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
