package com.chessinsights.dto;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InsightsDto {

    // --- Performance Rating ---
    private Integer actualRating;
    private Double performanceRating;
    private String performanceVerdict;

    // --- Performance Verdict ---

    private Double avgOpponentRating;
    private Double avgOpponentRatingWins;
    private Double avgOpponentRatingLosses;
    private Double avgOpponentRatingDraws;

    //actual rating 
    private Double avgPlayerRating;
    private Integer highestRating;
    private Integer lowestRating;

    // --- Streaks ---
    private Integer longestWinStreak;
    private Integer longestLossStreak;
    private Integer currentStreak;
    private String currentStreakType;

    // --- Tilt Detection ---
    private Integer tiltSessions;
    private List<TiltSession> tiltSessionDetails;

    // --- Opening Recommendations ---
    private List<OpeningRecommendation> recommendedOpenings;
    private List<OpeningRecommendation> avoidOpenings;

    // --- Playstyle ---
    private PlayStyle playStyle;

    // --- Rival ---
    private RivalStats topRival;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TiltSession {
        private Instant startedAt;
        private Instant endedAt;
        private Integer gamesPlayed;
        private Integer losses;
        private Integer ratingDrop;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OpeningRecommendation {
        private String openingName;
        private String ecoCode;
        private Integer gamesPlayed;
        private Double winRate;
        private String reason;
        private String confidence;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlayStyle {
        private Double avgGameLength;
        private Double checkmateRate;
        private Double resignRate;
        private Double timeoutRate;
        private Double stalemateRate;
        private String tendency;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RivalStats {
        private String opponentUsername;
        private Integer totalGames;
        private Integer wins;
        private Integer losses;
        private Integer draws;
        private Double winRate;
        private String bestOpening;
    }
}