package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import lombok.Data; // @Getter, @Setter, @EqualsAndHashCode, @ToStringを自動生成
import lombok.NoArgsConstructor; // 引数なしのコンストラクタを自動生成 (必須)
import lombok.AllArgsConstructor; // 全フィールドのコンストラクタを自動生成 (任意)

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "reminders", schema = "public") // スキーマ名を確認
@Data // これ一つで getter, setter, toString, equals, hashCode を自動生成
@NoArgsConstructor // ★重要: デフォルトコンストラクタを自動生成
@AllArgsConstructor // 全フィールドを引数とするコンストラクタを自動生成 (任意)
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore // AppUser への ManyToOne 関連に JsonIgnore を付ける
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser appUser; // リマインダーの所有者

    @Column(name = "custom_title", nullable = false)
    private String customTitle; // 例: リマインダーのタイトル

    @Column(name = "description", length = 500)
    private String description; // 例: 詳細説明

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "remind_date", nullable = false)
    private LocalDate remindDate;

    @JsonFormat(pattern = "HH:mm:ss")
    @Column(name = "remind_time") // remindTime は null を許容する場合は nullable = true を指定 (デフォルト)
    private LocalTime remindTime;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false; // 例: リマインダーが完了したかどうか

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at") // updatedAt は null を許容する場合は nullable = true を指定 (デフォルト)
    private LocalDateTime updatedAt;

    @Column(name = "google_event_id") // Google CalendarのイベントID (nullable = true)
    private String googleEventId;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isCompleted == null) {
            this.isCompleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Reminder(String customTitle, LocalDate remindDate, LocalTime remindTime, String description,
            AppUser appUser) {
        this.customTitle = customTitle;
        this.remindDate = remindDate;
        this.remindTime = remindTime;
        this.description = description;
        this.appUser = appUser;
        this.isCompleted = false; // デフォルトで未完了
        // createdAt, updatedAt, googleEventId は @PrePersist で自動設定される
    }
}