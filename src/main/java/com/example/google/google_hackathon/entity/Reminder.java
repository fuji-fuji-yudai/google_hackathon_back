package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import lombok.Data; // @Getter, @Setter, @EqualsAndHashCode, @ToStringを自動生成
import lombok.NoArgsConstructor; // 引数なしのコンストラクタを自動生成
import lombok.AllArgsConstructor; // 全フィールドのコンストラクタを自動生成 (任意)

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reminders")
@Data // これ一つで getter, setter, toString, equals, hashCode を自動生成
@NoArgsConstructor // デフォルトコンストラクタを自動生成
@AllArgsConstructor // 全フィールドを引数とするコンストラクタを自動生成 (今回はカスタムコンストラクタと競合しないよう注意)
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customTitle; // 例: リマインダーのタイトル

    @Column(nullable = false)
    private LocalDate remindDate; // 例: 通知日

    @Column(nullable = false)
    private LocalTime remindTime; // 例: 通知時間

    @Column(nullable = true, length = 500) // descriptionはnullを許容する
    private String description; // 例: 詳細説明

    @Column(nullable = false)
    private String status = "PENDING"; // 例: "PENDING", "NOTIFIED", "COMPLETED" など

    // AppUserとの関連付け (以前の指示通り)
    @ManyToOne(fetch = FetchType.LAZY) // 複数のリマインダーが一人のAppUserに属する
    @JoinColumn(name = "app_user_id", nullable = false) // データベース上の外部キーカラム名。AppUserエンティティの主キーに紐づく
    private AppUser appUser; // リマインダーの所有者

    // カスタムコンストラクタ（特定のフィールドで初期化したい場合、@NoArgsConstructorと@AllArgsConstructorと併用可）
    // 例えば、IDやstatusを除いて初期化したい場合
    public Reminder(String customTitle, LocalDate remindDate, LocalTime remindTime, String description,
            AppUser appUser) {
        this.customTitle = customTitle;
        this.remindDate = remindDate;
        this.remindTime = remindTime;
        this.description = description;
        this.appUser = appUser;
        this.status = "PENDING"; // デフォルトでPENDING
    }
}