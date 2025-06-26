package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import lombok.Data; // @Getter, @Setter, @EqualsAndHashCode, @ToStringを自動生成
import lombok.NoArgsConstructor; // 引数なしのコンストラクタを自動生成
import lombok.AllArgsConstructor; // 全フィールドのコンストラクタを自動生成 (任意)

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime; // ★追加: LocalDateTimeをインポート

@Entity
@Table(name = "reminders", schema = "public")
@Data // これ一つで getter, setter, toString, equals, hashCode を自動生成
@NoArgsConstructor // デフォルトコンストラクタを自動生成
@AllArgsConstructor // 全フィールドを引数とするコンストラクタを自動生成
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // AppUserとの関連付け
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser appUser; // リマインダーの所有者

    @Column(name = "custom_title", nullable = false)
    private String customTitle; // 例: リマインダーのタイトル

    @Column(name = "description", length = 500) // descriptionはnullを許容する (nullable = trueは省略可)
    private String description; // 例: 詳細説明

    @Column(name = "remind_date", nullable = false)
    private LocalDate remindDate; // 例: 通知日

    @Column(name = "remind_time") // 通知時間が必ずしも必須ではない場合を考慮しnullable=true
    private LocalTime remindTime; // 例: 通知時間

    // status を isCompleted に変更
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false; // 例: リマインダーが完了したかどうか

    // 作成日時
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新日時
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Google CalendarのイベントID
    @Column(name = "google_event_id")
    private String googleEventId;

    // エンティティが永続化される直前に実行されるコールバック
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now(); // 作成時も更新時も初期設定
        if (this.isCompleted == null) { // nullの場合にfalseをセット
            this.isCompleted = false;
        }
    }

    // エンティティが更新される直前に実行されるコールバック
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // カスタムコンストラクタを更新後のフィールドに合わせて修正
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