package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "roadmap_entries")
public class RoadmapEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categoryName;

    @Column(nullable = false)
    private String taskName; // タスク名を追加

    @Column(nullable = false)
    private String startMonth; // month を startMonth に変更

    @Column(nullable = false)
    private String endMonth; // 終了月を追加

    // description フィールドを削除

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    // コンストラクタ
    public RoadmapEntry() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    // GetterとSetter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTaskName() { // タスク名のGetter
        return taskName;
    }

    public void setTaskName(String taskName) { // タスク名のSetter
        this.taskName = taskName;
    }

    public String getStartMonth() { // 開始月のGetter
        return startMonth;
    }

    public void setStartMonth(String startMonth) { // 開始月のSetter
        this.startMonth = startMonth;
    }

    public String getEndMonth() { // 終了月のGetter
        return endMonth;
    }

    public void setEndMonth(String endMonth) { // 終了月のSetter
        this.endMonth = endMonth;
    }

    // getDescription() と setDescription() を削除

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}