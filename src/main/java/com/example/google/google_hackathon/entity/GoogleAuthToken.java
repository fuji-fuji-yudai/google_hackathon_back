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

    // AppUserのIDを直接格納
    @Column(name = "user_id", nullable = false, unique = true)
    private Long appUserId;

    // GoogleのユーザーID (sub claim)
    @Column(name = "google_sub_id", unique = true, nullable = false)
    private String googleSubId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private Long expiresIn;

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