package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final AppUserRepository appUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(
            GoogleAuthTokenRepository googleAuthTokenRepository,
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
        OAuth2User oauth2User = super.loadUser(userRequest);
        logger.info("OAuth2User loaded: Name={}, Attributes={}", oauth2User.getName(), oauth2User.getAttributes());

        String googleSubId = oauth2User.getName();

        return googleAuthTokenRepository.findByGoogleSubId(googleSubId)
                .map(existingGoogleAuthToken -> {
                    logger.info("既存のGoogleAuthTokenが見つかりました。Google Sub ID: {}", googleSubId);
                    AppUser appUser = appUserRepository.findById(existingGoogleAuthToken.getAppUserId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "関連AppUserが見つかりません: " + existingGoogleAuthToken.getAppUserId()));

                    updateExistingUserAndToken(appUser, existingGoogleAuthToken, oauth2User, userRequest);
                    return createOAuth2User(appUser.getUsername(), oauth2User.getAttributes(),
                            Collections.singletonList("ROLE_USER"));
                })
                .orElseGet(() -> {
                    logger.info("新規のGoogleAuthTokenです。Google Sub ID: {}", googleSubId);
                    return registerNewUserAndToken(oauth2User, userRequest);
                });
    }

    private void updateExistingUserAndToken(AppUser appUser, GoogleAuthToken googleAuthToken, OAuth2User oauth2User,
            OAuth2UserRequest userRequest) {
        googleAuthToken.setAccessToken(userRequest.getAccessToken().getTokenValue());

        // ★削除: リフレッシュトークンの取得と保存ロジックを完全に削除しました。
        googleAuthToken.setRefreshToken(null); // 明示的にnullを設定

        // ★修正: expiryDate (DBでは expiry_date) を Instant から LocalDateTime に変換して設定
        if (userRequest.getAccessToken().getExpiresAt() != null) {
            googleAuthToken.setExpiryDate(
                    LocalDateTime.ofInstant(userRequest.getAccessToken().getExpiresAt(), ZoneId.systemDefault()));
        } else {
            googleAuthToken.setExpiryDate(null);
        }

        googleAuthToken.setScope(String.join(" ", userRequest.getAccessToken().getScopes()));
        googleAuthToken.setTokenType(userRequest.getAccessToken().getTokenType().getValue());
        googleAuthToken.setUpdatedAt(LocalDateTime.now());
        googleAuthTokenRepository.save(googleAuthToken);
        logger.info("既存のAppUserとGoogleAuthTokenを更新しました。AppUser ID: {}", appUser.getId());
    }

    private OAuth2User registerNewUserAndToken(OAuth2User oauth2User, OAuth2UserRequest userRequest) {
        String email = oauth2User.getAttribute("email");
        String usernameToUse = email != null ? email : "google_" + UUID.randomUUID().toString();

        AppUser appUser = appUserRepository.findByUsername(usernameToUse)
                .orElseGet(() -> {
                    logger.info("AppUserを新規作成します。ユーザー名: {}", usernameToUse);
                    AppUser newAppUser = new AppUser();
                    newAppUser.setUsername(usernameToUse);
                    newAppUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newAppUser.setRole("USER");
                    return appUserRepository.save(newAppUser);
                });

        GoogleAuthToken.GoogleAuthTokenBuilder tokenBuilder = GoogleAuthToken.builder()
                .appUserId(appUser.getId())
                .googleSubId(oauth2User.getName())
                .accessToken(userRequest.getAccessToken().getTokenValue());

        // ★削除: リフレッシュトークンの取得と保存ロジックを完全に削除しました。
        tokenBuilder.refreshToken(null); // 明示的にnullを設定

        // ★修正: expiryDate (DBでは expiry_date) を Instant から LocalDateTime に変換して設定
        if (userRequest.getAccessToken().getExpiresAt() != null) {
            tokenBuilder.expiryDate(
                    LocalDateTime.ofInstant(userRequest.getAccessToken().getExpiresAt(), ZoneId.systemDefault()));
        }

        tokenBuilder.scope(String.join(" ", userRequest.getAccessToken().getScopes()));
        tokenBuilder.tokenType(userRequest.getAccessToken().getTokenType().getValue());
        tokenBuilder.createdAt(LocalDateTime.now());
        tokenBuilder.updatedAt(LocalDateTime.now());

        GoogleAuthToken newGoogleAuthToken = tokenBuilder.build();
        googleAuthTokenRepository.save(newGoogleAuthToken);
        logger.info("AppUser (ID: {}) と新規GoogleAuthToken (ID: {}) を登録しました。", appUser.getId(),
                newGoogleAuthToken.getId());

        return createOAuth2User(appUser.getUsername(), oauth2User.getAttributes(),
                Collections.singletonList("ROLE_USER"));
    }

    private OAuth2User createOAuth2User(String username, Map<String, Object> attributes, java.util.List<String> roles) {
        return new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                roles.stream().map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(r))
                        .collect(java.util.stream.Collectors.toList()),
                attributes,
                "sub");
    }
}