package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "google_auth_tokens")
@Data // Getter, Setter, toString, equals, hashCode を自動生成
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // AppUserへの参照（ユーザーとトークンを紐付ける）
    // OneToOneリレーションシップで、user_idカラムを外部キーとして指定
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser appUser;

    @Column(name = "google_id", unique = true, nullable = false) // GoogleのユーザーID (subクレーム) を保存
    private String googleId;

    @Column(name = "access_token", length = 2048, nullable = false) // アクセストークンは長くなる可能性があるので、長めに設定
    private String accessToken;

    @Column(name = "refresh_token", length = 2048) // リフレッシュトークンは取得できない場合もあるためnullable
    private String refreshToken;

    @Column(name = "expiry_date", nullable = false) // トークンの有効期限
    private LocalDateTime expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // エンティティが永続化される直前に実行されるコールバック
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now(); // 作成時も更新時も初期設定
    }

    // エンティティが更新される直前に実行されるコールバック
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}