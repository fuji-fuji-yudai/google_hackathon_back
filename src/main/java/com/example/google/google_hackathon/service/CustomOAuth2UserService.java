package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.entity.Reminder; // Reminderエンティティをimport
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.repository.ReminderRepository; // ReminderRepositoryをimport

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder; // RequestContextHolderをimport
import org.springframework.web.context.request.ServletRequestAttributes; // ServletRequestAttributesをimport

import jakarta.servlet.http.HttpSession; // HttpSessionをimport

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final ReminderRepository reminderRepository; // ReminderRepositoryを追加
    private final GoogleCalendarService googleCalendarService; // GoogleCalendar連携を別サービスに切り出し

    public CustomOAuth2UserService(AppUserRepository appUserRepository,
            GoogleAuthTokenRepository googleAuthTokenRepository,
            ReminderRepository reminderRepository,
            GoogleCalendarService googleCalendarService) { // コンストラクタに追加
        this.appUserRepository = appUserRepository;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.reminderRepository = reminderRepository;
        this.googleCalendarService = googleCalendarService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        String googleId = oauth2User.getName(); // GoogleのユーザーID (subクレーム)

        String accessToken = userRequest.getAccessToken().getTokenValue();
        String refreshToken = null; // OAuth2AccessTokenから直接RefreshTokenは取得できないため、nullをセット
        Instant expiryInstant = userRequest.getAccessToken().getExpiresAt();
        LocalDateTime expiryDate = (expiryInstant != null) ? LocalDateTime.ofInstant(expiryInstant, ZoneOffset.UTC)
                : null;

        AppUser appUser;
        Optional<GoogleAuthToken> existingGoogleAuthToken = googleAuthTokenRepository.findByGoogleId(googleId);

        if (existingGoogleAuthToken.isPresent()) {
            GoogleAuthToken authToken = existingGoogleAuthToken.get();
            appUser = authToken.getAppUser();

            if (appUser == null) {
                appUser = createGoogleLinkedAppUser(googleId, email);
                authToken.setAppUser(appUser);
            }

            // トークン情報を更新
            authToken.setAccessToken(accessToken);
            authToken.setRefreshToken(refreshToken);
            authToken.setExpiryDate(expiryDate);
            authToken.setUpdatedAt(LocalDateTime.now());
            googleAuthTokenRepository.save(authToken);

        } else {
            appUser = createGoogleLinkedAppUser(googleId, email);

            GoogleAuthToken newAuthToken = new GoogleAuthToken();
            newAuthToken.setAppUser(appUser);
            newAuthToken.setGoogleId(googleId);
            newAuthToken.setAccessToken(accessToken);
            newAuthToken.setRefreshToken(refreshToken);
            newAuthToken.setExpiryDate(expiryDate);
            newAuthToken.setCreatedAt(LocalDateTime.now());
            googleAuthTokenRepository.save(newAuthToken);
        }

        // ここからが新しいリダイレクト連携のロジック
        // セッションから一時的に保存したリマインダー情報を取得
        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest()
                .getSession();
        Long pendingReminderId = (Long) session.getAttribute("pendingReminderId");

        if (pendingReminderId != null) {
            Optional<Reminder> pendingReminderOpt = reminderRepository.findById(pendingReminderId);
            if (pendingReminderOpt.isPresent()) {
                Reminder pendingReminder = pendingReminderOpt.get();
                // Googleカレンダー連携を試みる
                googleCalendarService.createGoogleCalendarEvent(pendingReminder, appUser);
                // セッションからリマインダー情報を削除
                session.removeAttribute("pendingReminderId");
            }
        }

        return oauth2User;
    }

    private AppUser createGoogleLinkedAppUser(String googleId, String email) {
        String uniqueUsername = "google_" + googleId;

        AppUser newAppUser = new AppUser();
        newAppUser.setUsername(uniqueUsername);
        newAppUser.setPassword("{noop}" + UUID.randomUUID().toString());
        newAppUser.setRole("USER");

        return appUserRepository.save(newAppUser);
    }
}