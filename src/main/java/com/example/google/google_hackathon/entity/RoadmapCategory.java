package com.example.google.google_hackathon.entity;

import jakarta.persistence.*; // Spring Boot 3+ では javax.persistence ではなく jakarta.persistence を使用
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime; // LocalDateTime をインポート

@Entity
@Table(name = "roadmap_categories", schema = "public") // テーブル名を指定
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user; // user_id カラムに対応

    @Column(name = "name", nullable = false, unique = true) // 'name' カラムにマップし、ユニーク制約を追加
    private String name; // カテゴリとタスク名を結合した文字列を保存

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Entityが永続化される前に自動的にcreated_atとupdated_atを設定
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Entityが更新される前に自動的にupdated_atを設定
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 便利コンストラクタ（新しいエントリ作成用）
    public RoadmapCategory(AppUser user, String name) {
        this.user = user;
        this.name = name;
    }
}