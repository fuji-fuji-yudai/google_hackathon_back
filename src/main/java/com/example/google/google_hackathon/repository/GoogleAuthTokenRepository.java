package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleAuthTokenRepository extends JpaRepository<GoogleAuthToken, Long> {
    Optional<GoogleAuthToken> findByAppUser(AppUser appUser); // ログイン中のAppUserに紐づくトークンを検索

    Optional<GoogleAuthToken> findByGoogleId(String googleId); // Google IDでトークンを検索 (CustomOAuth2UserServiceで利用)
}