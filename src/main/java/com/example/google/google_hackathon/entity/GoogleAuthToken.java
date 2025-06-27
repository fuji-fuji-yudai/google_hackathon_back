package com.example.google.google_hackathon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "google_auth_tokens", schema = "auth") // データベースのテーブル名を指定
@Data // Lombokのアノテーション: getter, setter, equals, hashCode, toStringを自動生成
@NoArgsConstructor // Lombokのアノテーション: 引数なしコンストラクタを自動生成
@AllArgsConstructor // Lombokのアノテーション: 全てのフィールドを引数とするコンストラクタを自動生成
@Builder // Lombokのアノテーション: Builderパターンを自動生成
public class GoogleAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // IDを自動生成（例: MySQLの場合）
    private Long id;

    // このトークンが紐づくアプリケーション内のユーザーID
    // Spring SecurityのユーザーIDや、独自ユーザーIDなど
    private String userId;

    // OAuth2アクセストークン
    private String accessToken;

    // OAuth2リフレッシュトークン（オフラインアクセス用）
    private String refreshToken;

    // アクセストークンの有効期限（UnixタイムスタンプまたはInstantなど）
    private Long expiresIn;

    // トークンが発行されたスコープ（例: "https://www.googleapis.com/auth/calendar"）
    private String scope;

    // トークンタイプ（通常 "Bearer"）
    private String tokenType;

    // 作成日時
    private java.time.LocalDateTime createdAt;

    // 更新日時
    private java.time.LocalDateTime updatedAt;

}