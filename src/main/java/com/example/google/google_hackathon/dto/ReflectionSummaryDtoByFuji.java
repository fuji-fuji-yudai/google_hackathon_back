package com.example.google.google_hackathon.dto;

public class ReflectionSummaryDtoByFuji {
    private String yearMonth;
    private String activitySummary;
    private String achievementSummary;
    private String improvementSummary;

    public ReflectionSummaryDtoByFuji(String yearMonth, String activitySummary, String achievementSummary, String improvementSummary) {
        this.yearMonth = yearMonth;
        this.activitySummary = activitySummary;
        this.achievementSummary = achievementSummary;
        this.improvementSummary = improvementSummary;
    }

    // ゲッター
    public String getYearMonth() {
        return yearMonth;
    }

    public String getActivitySummary() {
        return activitySummary;
    }

    public String getAchievementSummary() {
        return achievementSummary;
    }

    public String getImprovementSummary() {
        return improvementSummary;
    }
}
