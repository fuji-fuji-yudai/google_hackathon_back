package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public CustomOAuth2UserService(AppUserRepository appUserRepository,
            GoogleAuthTokenRepository googleAuthTokenRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.appUserRepository = appUserRepository;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String googleId = oauth2User.getName(); // GoogleのユーザーID (subクレーム)

        // アクセストークンを取得
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // OAuth2AccessTokenから直接RefreshTokenは取得できないため、nullをセット
        String refreshToken = null;
        Instant expiryInstant = userRequest.getAccessToken().getExpiresAt();
        LocalDateTime expiryDate = (expiryInstant != null) ? LocalDateTime.ofInstant(expiryInstant, ZoneOffset.UTC)
                : null;

        // JWT認証用のAppUserを検索または作成
        Optional<AppUser> existingAppUser = appUserRepository.findByEmail(email);
        AppUser appUser;
        if (existingAppUser.isPresent()) {
            appUser = existingAppUser.get();
        } else {
            // 新規AppUserを作成（既存のJWT認証フローと共存するため）
            appUser = new AppUser();
            appUser.setUsername(email); // 例: emailをユーザー名とする
            appUser.setEmail(email); // AppUserエンティティにsetEmailが追加されたのでOK
            // OAuth2認証ユーザーなので、パスワードはダミー値を設定
            appUser.setPassword("{noop}" + UUID.randomUUID().toString());

            appUser = appUserRepository.save(appUser); // AppUserを先に保存してIDを取得
        }

        // GoogleAuthTokenの保存または更新
        Optional<GoogleAuthToken> existingGoogleAuthToken = googleAuthTokenRepository.findByAppUser(appUser);
        GoogleAuthToken googleAuthToken;
        if (existingGoogleAuthToken.isPresent()) {
            googleAuthToken = existingGoogleAuthToken.get();
            // 既存の場合、トークン情報を更新
            googleAuthToken.setGoogleId(googleId);
            googleAuthToken.setAccessToken(accessToken);
            googleAuthToken.setRefreshToken(refreshToken); // nullの可能性あり
            googleAuthToken.setExpiryDate(expiryDate);
            googleAuthToken.setUpdatedAt(LocalDateTime.now());
        } else {
            // 新規の場合、GoogleAuthTokenを作成
            googleAuthToken = new GoogleAuthToken();
            googleAuthToken.setAppUser(appUser); // AppUserと紐付ける
            googleAuthToken.setGoogleId(googleId);
            googleAuthToken.setAccessToken(accessToken);
            googleAuthToken.setRefreshToken(refreshToken); // nullの可能性あり
            googleAuthToken.setExpiryDate(expiryDate);
            googleAuthToken.setCreatedAt(LocalDateTime.now());
        }
        googleAuthTokenRepository.save(googleAuthToken);

        return oauth2User;
    }
}