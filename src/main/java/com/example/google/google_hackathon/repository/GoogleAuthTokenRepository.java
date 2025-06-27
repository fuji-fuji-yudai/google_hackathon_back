package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.GoogleAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Springにリポジトリであることを示す
public interface GoogleAuthTokenRepository extends JpaRepository<GoogleAuthToken, Long> {

    // userId（Google IDなど）でGoogleAuthTokenを検索するカスタムメソッド
    Optional<GoogleAuthToken> findByUserId(String userId);

    // 必要に応じて他の検索メソッドを追加できます
    // 例: Optional<GoogleAuthToken> findByRefreshToken(String refreshToken);
}