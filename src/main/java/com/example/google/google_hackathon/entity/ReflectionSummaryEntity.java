package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reflection_summaries",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "year_month"}) // ユニーク制約を設定
    }
)
public class ReflectionSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 主キー

    @Column(name = "user_id", nullable = false)
    private Long userId; // ユーザーID

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth; // 年月 (フォーマット例: "2023-10")

    @Column(name = "activity_summary", nullable = false, columnDefinition = "TEXT")
    private String activitySummary; // 活動内容の要約

    @Column(name = "achievement_summary", nullable = false, columnDefinition = "TEXT")
    private String achievementSummary; // 達成事項の要約

    @Column(name = "improvement_summary", nullable = false, columnDefinition = "TEXT")
    private String improvementSummary; // 改善点の要約

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // 作成日時

    // ゲッターとセッター
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public String getActivitySummary() {
        return activitySummary;
    }

    public void setActivitySummary(String activitySummary) {
        this.activitySummary = activitySummary;
    }

    public String getAchievementSummary() {
        return achievementSummary;
    }

    public void setAchievementSummary(String achievementSummary) {
        this.achievementSummary = achievementSummary;
    }

    public String getImprovementSummary() {
        return improvementSummary;
    }

    public void setImprovementSummary(String improvementSummary) {
        this.improvementSummary = improvementSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
