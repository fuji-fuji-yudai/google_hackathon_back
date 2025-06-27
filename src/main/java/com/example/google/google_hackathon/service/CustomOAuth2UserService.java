package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository; // AppUserRepositoryをインポート
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.security.JwtTokenProvider; // JwtTokenProviderをインポート
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final AppUserRepository appUserRepository; // フィールドを追加
    private final JwtTokenProvider jwtTokenProvider; // フィールドを追加

    // コンストラクタを更新: SecurityConfig.javaからの引数に合わせる
    public CustomOAuth2UserService(GoogleAuthTokenRepository googleAuthTokenRepository,
            AppUserRepository appUserRepository, // 引数を追加
            JwtTokenProvider jwtTokenProvider) { // 引数を追加
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.appUserRepository = appUserRepository; // フィールドを初期化
        this.jwtTokenProvider = jwtTokenProvider; // フィールドを初期化
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // デフォルトのOAuth2UserServiceを使ってユーザー情報をロード
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // OAuth2AccessToken の情報
        OAuth2AccessToken accessToken = userRequest.getAccessToken();

        String refreshToken = null;

        // ユーザーのGoogle ID (sub claim) を取得
        String googleId = oAuth2User.getName(); // Spring Securityは通常 'sub' を name として扱う

        Optional<GoogleAuthToken> existingToken = googleAuthTokenRepository.findByUserId(googleId);

        GoogleAuthToken googleAuthToken;
        if (existingToken.isPresent()) {
            googleAuthToken = existingToken.get();
            // トークン情報を更新
            googleAuthToken.setAccessToken(accessToken.getTokenValue());
            // Instant から Long (Unixタイムスタンプ) へ変換
            if (accessToken.getExpiresAt() != null) {
                googleAuthToken.setExpiresIn(accessToken.getExpiresAt().getEpochSecond());
            }
            googleAuthToken.setScope(String.join(",", accessToken.getScopes()));
            googleAuthToken.setTokenType(accessToken.getTokenType().getValue());
            googleAuthToken.setUpdatedAt(LocalDateTime.now());

        } else {
            // 新しいトークンを保存
            googleAuthToken = GoogleAuthToken.builder()
                    .userId(googleId) // アプリケーションユーザーと紐付けるID (ここでは仮にGoogle IDを使用)
                    .accessToken(accessToken.getTokenValue())
                    .refreshToken(refreshToken) // ここは null になる可能性が高いです
                    .expiresIn(accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().getEpochSecond() : null)
                    .scope(String.join(",", accessToken.getScopes()))
                    .tokenType(accessToken.getTokenType().getValue())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }
        googleAuthTokenRepository.save(googleAuthToken);

        // JWTトークンプロバイダなど、他のサービスを使う場合はここでロジックを追加
        // 例: String jwt = jwtTokenProvider.generateToken(oAuth2User); // 必要に応じて実装

        return oAuth2User;
    }
}
