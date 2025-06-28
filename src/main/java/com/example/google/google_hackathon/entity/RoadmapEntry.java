package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "roadmap_entries", schema = "public")
public class RoadmapEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "start_month", nullable = false)
    private Integer startMonth;

    @Column(name = "end_month", nullable = false)
    private Integer endMonth;

    @Column(name = "start_year", nullable = false)
    private Integer startYear;

    @Column(name = "end_year", nullable = false)
    private Integer endYear;

    // createdAtフィールド: データベースのTIMESTAMP型と、JSONシリアライズの形式を定義
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") // ★追加: JSON出力フォーマットを指定
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // updatedAtフィールド: データベースのTIMESTAMP型と、JSONシリアライズの形式を定義
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") // ★追加: JSON出力フォーマットを指定
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // @Column(name = "created_at", nullable = false)
    // private LocalDateTime createdAt;

    // @Column(name = "updated_at", nullable = false)
    // private LocalDateTime updatedAt;

    public RoadmapEntry() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // コンストラクタを更新する場合（推奨）
    public RoadmapEntry(AppUser user, String categoryName, String taskName, Integer startMonth, Integer endMonth,
            Integer startYear, Integer endYear) {
        this.user = user;
        this.categoryName = categoryName;
        this.taskName = taskName;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.startYear = startYear;
        this.endYear = endYear;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Integer getStartMonth() {
        return startMonth;
    }

    public void setStartMonth(Integer startMonth) {
        this.startMonth = startMonth;
    }

    public Integer getEndMonth() {
        return endMonth;
    }

    public void setEndMonth(Integer endMonth) {
        this.endMonth = endMonth;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public void setStartYear(Integer startYear) {
        this.startYear = startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public void setEndYear(Integer endYear) {
        this.endYear = endYear;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}