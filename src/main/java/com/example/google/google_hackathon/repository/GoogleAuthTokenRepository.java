package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.entity.AppUser; // AppUserエンティティをインポート
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Spring Data JPA がこのインターフェースをリポジトリとして認識
public interface GoogleAuthTokenRepository extends JpaRepository<GoogleAuthToken, Long> {
    // AppUserオブジェクトを直接指定して、それに関連するGoogleAuthTokenを検索
    Optional<GoogleAuthToken> findByAppUser(AppUser appUser);

    // GoogleのユーザーID（subクレーム）を使ってGoogleAuthTokenを検索
    Optional<GoogleAuthToken> findByGoogleId(String googleId);
}