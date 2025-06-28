package com.example.google.google_hackathon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "google_auth_tokens", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ★修正: DBのカラム名 'app_user_id' に正確に合わせます。
    // unique = true はDBの定義に合わせます。
    @Column(name = "app_user_id", nullable = false, unique = true)
    private Long appUserId;

    // ★修正: DBのカラム名 'google_id' に正確に合わせます。
    // AppUserRepositoryのクエリが 'googleSubId' を参照するため、Javaフィールド名は 'googleSubId' のまま、
    // DBへのマッピングで 'google_id' を指定します。
    @Column(name = "google_id", unique = true, nullable = false)
    private String googleSubId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    // DBにはrefresh_token列がありますが、現行のOAuth2UserRequestから取得できないため、コードからはnullをセットします。
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    // ★修正: DBのカラム名 'expiry_date' に正確に合わせ、型を LocalDateTime に変更します。
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(length = 50)
    private String tokenType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}