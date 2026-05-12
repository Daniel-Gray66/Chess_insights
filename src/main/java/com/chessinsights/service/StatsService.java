package com.chessinsights.service;

import com.chessinsights.entity.ChessGame;
import com.chessinsights.entity.User;
import com.chessinsights.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final GameRepository gameRepository;

    @Cacheable(value = "statsCount", key = "#user.id + '-' + #timeClass + '-' + #color + '-' + #from")
    public long countGames(User user, String timeClass, String color, Instant from) {
        return getFilteredGames(user, timeClass, color, from).size();
    }

    @Cacheable(value = "statsAccuracy", key = "#user.id + '-' + #timeClass + '-' + #color + '-' + #from")
    public Double getAverageAccuracy(User user, String timeClass, String color, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, color, from);
        OptionalDouble avg = games.stream()
                .filter(g -> g.getAccuracy() != null)
                .mapToDouble(ChessGame::getAccuracy)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : null;
    }

    @Cacheable(value = "statsOpponentRating", key = "#user.id + '-' + #timeClass + '-' + #color + '-' + #from")
    public Double getAverageOpponentRating(User user, String timeClass, String color, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, color, from);
        OptionalDouble avg = games.stream()
                .mapToInt(ChessGame::getOpponentRating)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : null;
    }

    @Cacheable(value = "statsRecord", key = "#user.id + '-' + #timeClass + '-' + #color + '-' + #from")
    public Map<String, Object> getOverallRecord(User user, String timeClass, String color, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, color, from);

        long wins = games.stream().filter(g -> "win".equals(g.getResult())).count();
        long losses = games.stream().filter(g -> "loss".equals(g.getResult())).count();
        long draws = games.stream().filter(g -> "draw".equals(g.getResult())).count();
        long total = wins + losses + draws;

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("total", total);
        record.put("wins", wins);
        record.put("losses", losses);
        record.put("draws", draws);
        record.put("winRate", total > 0 ? round((double) wins / total * 100) : 0);
        record.put("lossRate", total > 0 ? round((double) losses / total * 100) : 0);
        record.put("drawRate", total > 0 ? round((double) draws / total * 100) : 0);

        return record;
    }

    @Cacheable(value = "statsColor", key = "#user.id + '-' + #timeClass + '-' + #from")
    public Map<String, Object> getColorBreakdown(User user, String timeClass, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, null, from);

        Map<String, Object> response = new LinkedHashMap<>();
        for (String c : List.of("white", "black")) {
            long wins = games.stream()
                    .filter(g -> c.equals(g.getUserColor()) && "win".equals(g.getResult())).count();
            long losses = games.stream()
                    .filter(g -> c.equals(g.getUserColor()) && "loss".equals(g.getResult())).count();
            long draws = games.stream()
                    .filter(g -> c.equals(g.getUserColor()) && "draw".equals(g.getResult())).count();
            long total = wins + losses + draws;

            Map<String, Object> colorData = new LinkedHashMap<>();
            colorData.put("win", wins);
            colorData.put("loss", losses);
            colorData.put("draw", draws);
            colorData.put("total", total);
            colorData.put("winRate", total > 0 ? round((double) wins / total * 100) : 0);
            response.put(c, colorData);
        }

        return response;
    }

    @Cacheable(value = "statsOpenings", key = "#user.id + '-' + #timeClass + '-' + #color + '-' + #from")
    public List<Map<String, Object>> getOpeningStats(User user, String timeClass, String color, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, color, from);

        Map<String, Map<String, Object>> openingMap = new LinkedHashMap<>();
        for (ChessGame game : games) {
            String openingName = game.getOpeningName();
            if (openingName == null) continue;

            openingMap.computeIfAbsent(openingName, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("opening", openingName);
                m.put("ecoCode", game.getEcoCode());
                m.put("wins", 0L);
                m.put("losses", 0L);
                m.put("draws", 0L);
                return m;
            });

            Map<String, Object> stats = openingMap.get(openingName);
            switch (game.getResult()) {
                case "win" -> stats.put("wins", (Long) stats.get("wins") + 1);
                case "loss" -> stats.put("losses", (Long) stats.get("losses") + 1);
                case "draw" -> stats.put("draws", (Long) stats.get("draws") + 1);
            }
        }

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

    @Cacheable(value = "statsRating", key = "#user.id + '-' + #timeClass + '-' + #from")
    public List<Map<String, Object>> getRatingProgression(User user, String timeClass, Instant from) {
        List<ChessGame> games = getFilteredGames(user, timeClass, null, from);

        return games.stream()
                .sorted(Comparator.comparing(ChessGame::getPlayedAt))
                .map(g -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("playedAt", g.getPlayedAt().toString());
                    point.put("rating", g.getUserRating());
                    point.put("timeClass", g.getTimeClass());
                    return point;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "statsTimeOfDay", key = "#user.id")
    public List<Map<String, Object>> getTimeOfDayStats(User user) {
        List<Object[]> results = gameRepository.countByHourOfDayAndResult(user.getId());
        return aggregateTimeStats(results, "hour", 24);
    }

    @Cacheable(value = "statsDayOfWeek", key = "#user.id")
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

    private List<ChessGame> getFilteredGames(User user, String timeClass, String color, Instant from) {
        List<ChessGame> games = gameRepository.findByUserOrderByPlayedAtDesc(user);

        return games.stream()
                .filter(g -> timeClass == null || timeClass.equalsIgnoreCase(g.getTimeClass()))
                .filter(g -> color == null || color.equalsIgnoreCase(g.getUserColor()))
                .filter(g -> from == null || g.getPlayedAt().isAfter(from))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> aggregateTimeStats(List<Object[]> results,
                                                          String periodKey, int periods) {
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