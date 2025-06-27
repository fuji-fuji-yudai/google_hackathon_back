package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.GoogleAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleAuthTokenRepository extends JpaRepository<GoogleAuthToken, Long> {

    // googleSubId で GoogleAuthToken を検索するカスタムメソッド
    Optional<GoogleAuthToken> findByGoogleSubId(String googleSubId);

    // 必要に応じて他の検索メソッドを追加できます
}