package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID; // UUID をインポートしてユニークなユーザー名を生成

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final AppUserRepository appUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(GoogleAuthTokenRepository googleAuthTokenRepository,
            AppUserRepository appUserRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.appUserRepository = appUserRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        OAuth2AccessToken accessToken = userRequest.getAccessToken();
        String refreshToken = null; // OAuth2UserRequestから直接は取得できないため、一旦null

        String googleSubId = oAuth2User.getName(); // Googleのユーザー識別子 (sub claim)
        String googleEmail = oAuth2User.getAttribute("email"); // Googleから提供されるメールアドレス (今回はusernameには使用しない)

        AppUser appUser;
        GoogleAuthToken googleAuthToken;

        // googleSubId を使って GoogleAuthToken を検索し、AppUserをロードする
        Optional<GoogleAuthToken> existingToken = googleAuthTokenRepository.findByGoogleSubId(googleSubId);

        if (existingToken.isPresent()) {
            // 既存のGoogle認証トークンが見つかった場合
            googleAuthToken = existingToken.get();
            // 関連するAppUserをappUserIdでロード
            appUser = appUserRepository.findById(googleAuthToken.getAppUserId())
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            "Associated AppUser not found for GoogleAuthToken."));

            // トークン情報を更新
            googleAuthToken.setAccessToken(accessToken.getTokenValue());
            if (accessToken.getExpiresAt() != null) {
                googleAuthToken.setExpiresIn(accessToken.getExpiresAt().getEpochSecond());
            }
            googleAuthToken.setScope(String.join(",", accessToken.getScopes()));
            googleAuthToken.setTokenType(accessToken.getTokenType().getValue());
            googleAuthToken.setUpdatedAt(LocalDateTime.now());

        } else {
            // 新規のGoogle認証（初回ログイン）の場合
            // ★ 新しいAppUserを作成し、ユニークなusernameを生成する
            appUser = new AppUser();
            // 例: "google_" + UUID の一部 を username とする
            // もしくは、GoogleのdisplayNameなどを利用しても良いですが、一意性を確保することが重要です。
            String uniqueUsername = "google_" + UUID.randomUUID().toString().substring(0, 8);
            appUser.setUsername(uniqueUsername);

            // OAuthユーザー用のダミーパスワードを設定しエンコード
            appUser.setPassword(passwordEncoder.encode("oauth2user_dummy_password_" + UUID.randomUUID().toString())); // 安全のためランダムなパスワード
            appUser.setRole("USER"); // デフォルトロール
            appUserRepository.save(appUser); // 新しいAppUserをDBに保存

            // 新しいGoogleAuthTokenを作成し、AppUserのIDと紐付ける
            googleAuthToken = GoogleAuthToken.builder()
                    .appUserId(appUser.getId()) // ★ 新しく作成したAppUserのIDを設定
                    .googleSubId(googleSubId)
                    .accessToken(accessToken.getTokenValue())
                    .refreshToken(refreshToken)
                    .expiresIn(accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().getEpochSecond() : null)
                    .scope(String.join(",", accessToken.getScopes()))
                    .tokenType(accessToken.getTokenType().getValue())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }
        googleAuthTokenRepository.save(googleAuthToken); // GoogleAuthTokenをDBに保存/更新

        return oAuth2User;
    }
}