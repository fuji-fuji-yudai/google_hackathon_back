package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime; // LocalDateTime をインポート
import org.hibernate.annotations.CreationTimestamp; // Hibernateアノテーションをインポート
import org.hibernate.annotations.UpdateTimestamp; // Hibernateアノテーションをインポート

@Entity
@Table(name = "app_user", schema = "auth") // authスキーマを使用
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false) // usernameは必須かつユニーク
    private String username;
    private String password; // ログインパスワード (OAuth2の場合はnullの場合も)
    private String role; // ユーザーの役割 (例: USER, ADMIN)

    // OAuth2認証で取得するメールアドレスを追加
    @Column(unique = true) // emailもユニークであるべき
    private String email;

    // 作成日時 (自動設定)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新日時 (自動設定)
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "google_access_token")
    private String googleAccessToken;

    @Column(name = "google_refresh_token")
    private String googleRefreshToken;

    // --- Getter メソッド ---
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getGoogleAccessToken() {
        return googleAccessToken;
    }

    public String getGoogleRefreshToken() {
        return googleRefreshToken;
    }

    // --- Setter メソッド（登録用に必要） ---
    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setEmail(String email) { // ★追加
        this.email = email;
    }

    public void setCreatedAt(LocalDateTime createdAt) { // (もし手動で設定する必要がある場合)
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) { // (もし手動で設定する必要がある場合)
        this.updatedAt = updatedAt;
    }

    public void setGoogleAccessToken(String googleAccessToken) {
        this.googleAccessToken = googleAccessToken;
    }

    public void setGoogleRefreshToken(String googleRefreshToken) {
        this.googleRefreshToken = googleRefreshToken;
    }

    // デフォルトコンストラクタ（JPAが必要とする）
    public AppUser() {
    }

    // コンストラクタ例（Lombokの@NoArgsConstructor/@AllArgsConstructorがない場合）
    public AppUser(String username, String password, String role, String email) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.email = email;
    }
}
