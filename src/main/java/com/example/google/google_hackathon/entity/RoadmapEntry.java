package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime; // createdAt, updatedAt 用

@Entity
@Table(name = "roadmap_entries")
public class RoadmapEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ManyToOne関係でAppUserエンティティと紐付ける
    // user_idカラムはデータベースではBIGINTだが、Java側ではAppUserオブジェクトとして扱う
    @ManyToOne(fetch = FetchType.LAZY) // LAZYはパフォーマンスのため推奨
    @JoinColumn(name = "user_id", nullable = false) // データベースの user_id カラムとマッピング
    private AppUser user; // user_id に対応する AppUser オブジェクト

    // AppUserオブジェクトのゲッターとセッター
    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "start_month", nullable = false)
    private String startMonth;

    @Column(name = "end_month", nullable = false)
    private String endMonth;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    // コンストラクタ
    public RoadmapEntry() {
        // AppUserエンティティにcreatedAt/updatedAtがないため、
        // ここではRoadmapEntry自身のもののみ初期化します。
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    // 既存のゲッターとセッター (変更なし)
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

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getStartMonth() {
        return startMonth;
    }

    public void setStartMonth(String startMonth) {
        this.startMonth = startMonth;
    }

    public String getEndMonth() {
        return endMonth;
    }

    public void setEndMonth(String endMonth) {
        this.endMonth = endMonth;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}