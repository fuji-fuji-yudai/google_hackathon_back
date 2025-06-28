package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse; // これはrefreshToken()の戻り値とは直接関係ないが、関連クラスとして残す
import com.google.api.client.auth.oauth2.TokenErrorResponse; // 同上
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential; // ★重要: これを使用
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.Optional;

// import com.google.api.client.auth.oauth2.CredentialRefreshListener; // ★削除: このリスナーはGoogleCredentialには直接セットできない

/**
 * Google Calendar API と連携するためのサービスです。
 * データベースに保存されたアクセストークンを使用して、Googleカレンダーのイベントを操作します。
 */
@Service
public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final AppUserRepository appUserRepository;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    private static final long FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

    public GoogleCalendarService(AppUserRepository appUserRepository,
            GoogleAuthTokenRepository googleAuthTokenRepository,
            ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 認証情報から Google Calendar API を使うための Credential を作って返すよ。
     * ここでは、SecurityContextから取得したユーザーIDを使ってDBからトークンを取得するよ。
     *
     * @param authentication 認証情報 (JWTによって認証されたユーザーの情報)
     * @return Google Calendar API を使うための Credential
     * @throws IllegalStateException    ユーザーやトークンが見つからない場合、またはトークンが無効な場合
     * @throws IOException              HTTP通信エラーなど
     * @throws GeneralSecurityException セキュリティ関連のエラー
     */
    private Credential getCredential(Authentication authentication) throws IOException, GeneralSecurityException {
        String googleSubId = authentication.getName();
        logger.debug("Credential取得中。Google Sub ID: {}", googleSubId);

        Optional<GoogleAuthToken> optionalAuthToken = googleAuthTokenRepository.findByGoogleSubId(googleSubId);

        if (optionalAuthToken.isEmpty()) {
            logger.error("重大なデータ不整合: GoogleAuthTokenが見つからない。Google ID: {}", googleSubId);
            throw new IllegalStateException("Googleログイン情報が見つかりません。再ログインを試してください。");
        }

        GoogleAuthToken googleAuthToken = optionalAuthToken.get();
        String accessToken = googleAuthToken.getAccessToken();
        String refreshToken = googleAuthToken.getRefreshToken();

        ZoneId tokenZoneId = ZoneId.systemDefault(); // または ZoneId.of("Asia/Tokyo");
        Long expiresAtMillis = googleAuthToken.getExpiryDate().atZone(tokenZoneId).toInstant().toEpochMilli();

        if (accessToken == null || accessToken.isEmpty()) {
            logger.error("アクセストークンがGoogleAuthToken (ID: {}) から見つからない、または空です。", googleAuthToken.getId());
            throw new IllegalStateException("Googleアクセストークンが見つかりません。");
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(googleClientId, googleClientSecret)
                .build()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpirationTimeMilliseconds(expiresAtMillis);

        // setExpiresInSeconds はGoogleCredentialが自動リフレッシュを判断するために内部的に使用しますが、
        // 明示的に設定する必要はありません。GoogleCredentialのexpirationTimeMillisecondsから自動的に計算されます。
        // long remainingSeconds = (expiresAtMillis - System.currentTimeMillis()) /
        // 1000;
        // credential.setExpiresInSeconds(Math.max(0, remainingSeconds));

        // ★削除: GoogleCredential には setRefreshListeners メソッドは存在しません。
        // credential.setRefreshListeners(java.util.Collections.singleton(new
        // CredentialRefreshListener() {
        // @Override
        // public void onTokenResponse(Credential credential, TokenResponse
        // tokenResponse) throws IOException {
        // logger.info("GoogleCredentialがアクセストークンをリフレッシュしました。DBを更新します。");
        // googleAuthToken.setAccessToken(credential.getAccessToken());
        // LocalDateTime newExpiryDate =
        // Instant.ofEpochMilli(credential.getExpirationTimeMilliseconds())
        // .atZone(tokenZoneId)
        // .toLocalDateTime();
        // googleAuthToken.setExpiryDate(newExpiryDate);
        // if (credential.getRefreshToken() != null &&
        // !credential.getRefreshToken().isEmpty()) {
        // googleAuthToken.setRefreshToken(credential.getRefreshToken());
        // }
        // googleAuthTokenRepository.save(googleAuthToken);
        // logger.info("データベースのGoogleAuthTokenを更新しました。新しい有効期限: {}", newExpiryDate);
        // }
        // @Override
        // public void onTokenErrorResponse(Credential credential, TokenErrorResponse
        // tokenErrorResponse) throws IOException {
        // logger.error("GoogleCredentialがアクセストークンのリフレッシュに失敗しました: {}",
        // tokenErrorResponse.getErrorDescription());
        // throw new IOException("アクセストークンのリフレッシュに失敗しました: " +
        // tokenErrorResponse.getErrorDescription());
        // }
        // }));

        // ★修正: ここで明示的にrefreshToken()を呼び出し、その結果を基にDBを更新する
        if (credential.getRefreshToken() != null
                && (System.currentTimeMillis() + FIVE_MINUTES_IN_MILLIS > credential.getExpirationTimeMilliseconds())) {
            logger.info("アクセストークンが期限切れ、または期限が近い ({}) です。リフレッシュを試みます。",
                    Instant.ofEpochMilli(credential.getExpirationTimeMilliseconds()).atZone(tokenZoneId)
                            .toLocalDateTime());
            try {
                boolean refreshed = credential.refreshToken(); // リフレッシュを試みる
                if (refreshed) {
                    logger.info("GoogleCredential.refreshToken() でトークンをリフレッシュしました。DBを更新します。");
                    // リフレッシュ成功後、Credentialオブジェクト自身が新しいトークン情報を持つので、それをDBに保存
                    googleAuthToken.setAccessToken(credential.getAccessToken());
                    // 新しい有効期限をLocalDateTimeに変換して設定
                    LocalDateTime newExpiryDate = Instant.ofEpochMilli(credential.getExpirationTimeMilliseconds())
                            .atZone(tokenZoneId) // 同じタイムゾーンを使用
                            .toLocalDateTime();
                    googleAuthToken.setExpiryDate(newExpiryDate);
                    // リフレッシュトークンが更新された場合（通常は初回のみ）、それも保存
                    if (credential.getRefreshToken() != null && !credential.getRefreshToken().isEmpty() &&
                            !credential.getRefreshToken().equals(googleAuthToken.getRefreshToken())) {
                        googleAuthToken.setRefreshToken(credential.getRefreshToken());
                    }
                    googleAuthTokenRepository.save(googleAuthToken);
                    logger.info("データベースのGoogleAuthTokenを更新しました。新しい有効期限: {}", newExpiryDate);
                } else {
                    logger.warn("GoogleCredential.refreshToken() がリフレッシュに失敗したか、不要でした。リフレッシュトークン: {}",
                            refreshToken != null ? "あり" : "なし");
                    // ここで refreshed が false の場合でも、通常はリフレッシュ不要と判断されたケース。
                    // しかし、期限切れでリフレッシュトークンも無い場合はエラーとする。
                    if (refreshToken == null || refreshToken.isEmpty()) {
                        throw new IllegalStateException("アクセストークンが期限切れで、リフレッシュトークンがありません。再ログインが必要です。");
                    }
                }
            } catch (IOException e) {
                logger.error("GoogleCredential.refreshToken() の呼び出し中にエラーが発生しました: {}", e.getMessage());
                throw new IllegalStateException("Google認証トークンのリフレッシュに失敗しました。再ログインを試してください。", e);
            }
        } else {
            logger.debug("アクセストークンは有効期限内か、リフレッシュトークンがありません。リフレッシュは不要です。");
        }

        logger.debug("Credentialの構築と必要に応じたリフレッシュを完了しました。");
        return credential;
    }

    /**
     * Google Calendar に新しいイベントを作るよ。
     *
     * @param authentication ログインしている人の情報だよ
     * @param summary        イベントの短い説明だよ
     * @param description    イベントの詳しい説明だよ
     * @param startDateTime  イベントが始まる日時だよ
     * @param endDateTime    イベントが終わる日時だよ
     * @param timeZone       イベントのタイムゾーンだよ (例: "Asia/Tokyo")
     * @return 作ったイベントの情報（JSON形式）だよ
     * @throws IOException              通信エラーがあったら
     * @throws GeneralSecurityException 認証やセキュリティのエラーがあったら
     * @throws GoogleAuthException      認証が必要な場合
     */
    public JsonNode createGoogleCalendarEvent(
            Authentication authentication,
            String summary, String description,
            LocalDateTime startDateTime, LocalDateTime endDateTime, String timeZone)
            throws IOException, GeneralSecurityException, GoogleAuthException {
        logger.info("Googleカレンダーイベント作成を開始します。Summary: {}", summary);

        try {
            Credential credential = getCredential(authentication);

            Calendar service = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential)
                    .setApplicationName("Google Hackathon App")
                    .build();

            com.google.api.client.util.DateTime start = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(startDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli());
            com.google.api.client.util.DateTime end = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(endDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli());

            Event event = new Event()
                    .setSummary(summary)
                    .setDescription(description)
                    .setStart(new EventDateTime().setDateTime(start).setTimeZone(timeZone))
                    .setEnd(new EventDateTime().setDateTime(end).setTimeZone(timeZone));

            String calendarId = "primary";
            logger.info("Google Calendar API を呼び出し中... カレンダーID: {}, イベント概要: {}", calendarId, summary);
            Event createdEvent = service.events().insert(calendarId, event).execute();
            logger.info("Google Calendar API 呼び出し完了。Created Event ID: {}", createdEvent.getId());

            return objectMapper.convertValue(createdEvent, JsonNode.class);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google Calendar API エラーレスポンス (JSON): {}", e.getDetails().getMessage(), e);
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                logger.warn("Google Calendar APIからの認証/権限エラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage());
                throw new GoogleAuthException("Google Calendar APIへのアクセスに認証が必要です", getGoogleAuthUrl());
            } else {
                logger.error("Google Calendar APIからの予期せぬエラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage(),
                        e);
                throw new IOException("Google Calendar APIエラー: " + e.getDetails().getMessage(), e);
            }
        } catch (IOException e) {
            logger.error("Google Calendar API呼び出し中のI/Oエラー: {}", e.getMessage(), e);
            throw e;
        } catch (GeneralSecurityException e) {
            logger.error("Google Calendar API認証情報のセキュリティエラー: {}", e.getMessage(), e);
            throw e;
        } catch (IllegalStateException e) {
            logger.error("Credential取得エラー: {}", e.getMessage(), e);
            throw new GoogleAuthException("認証情報が見つからない、または不正です。", getGoogleAuthUrl());
        }
    }

    /**
     * Google Calendar のイベント一覧をもらう
     *
     * @param authentication ログインしている人の情報だよ
     * @param timeMin        イベントの開始日時（これ以降）だよ
     * @param timeMax        イベントの終了日時（これ以前）だよ
     * @param timeZone       タイムゾーンだよ
     * @return イベント一覧の情報（JSON形式）だよ
     * @throws IOException              通信エラーがあったら
     * @throws GeneralSecurityException 認証やセキュリティのエラーがあったら
     * @throws GoogleAuthException      認証が必要な場合
     */
    public JsonNode listGoogleCalendarEvents(
            Authentication authentication,
            LocalDateTime timeMin, LocalDateTime timeMax, String timeZone)
            throws IOException, GeneralSecurityException, GoogleAuthException {
        logger.info("Googleカレンダーイベント一覧取得を開始します。TimeMin: {}, TimeMax: {}", timeMin, timeMax);

        try {
            Credential credential = getCredential(authentication);

            Calendar service = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential)
                    .setApplicationName("Google Hackathon App")
                    .build();

            com.google.api.client.util.DateTime minDateTime = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(timeMin, ZoneId.of(timeZone)).toInstant().toEpochMilli());
            com.google.api.client.util.DateTime maxDateTime = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(timeMax, ZoneId.of(timeZone)).toInstant().toEpochMilli());

            String calendarId = "primary";
            logger.info("Google Calendar API (List) を呼び出し中... カレンダーID: {}", calendarId);
            Events events = service.events().list(calendarId)
                    .setTimeMin(minDateTime)
                    .setTimeMax(maxDateTime)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setQ("event")
                    .execute();
            logger.info("Google Calendar API (List) 呼び出し完了。{} 件のイベントが見つかりました。",
                    events.getItems() != null ? events.getItems().size() : 0);

            return objectMapper.convertValue(events, JsonNode.class);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google Calendar API エラーレスポンス (JSON): {}", e.getDetails().getMessage(), e);
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                logger.warn("Google Calendar APIからの認証/権限エラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage());
                throw new GoogleAuthException("Google Calendar APIへのアクセスに認証が必要です", getGoogleAuthUrl());
            } else {
                logger.error("Google Calendar APIからの予期せぬエラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage(),
                        e);
                throw new IOException("Google Calendar APIエラー: " + e.getDetails().getMessage(), e);
            }
        } catch (IOException e) {
            logger.error("Google Calendar API呼び出し中のI/Oエラー: {}", e.getMessage(), e);
            throw e;
        } catch (GeneralSecurityException e) {
            logger.error("Google Calendar API認証情報のセキュリティエラー: {}", e.getMessage(), e);
            throw e;
        } catch (IllegalStateException e) {
            logger.error("Credential取得エラー: {}", e.getMessage(), e);
            throw new GoogleAuthException("認証情報が見つからない、または不正です。", getGoogleAuthUrl());
        }
    }

    /**
     * Google認証同意画面へのリダイレクトURLを生成するメソッド。
     *
     * @return Google認証同意画面へのURL
     */
    private String getGoogleAuthUrl() {
        logger.warn("getGoogleAuthUrl() メソッドが未実装です。正しい認証URLを返すようにしてください。");

        try {
            return "https://accounts.google.com/o/oauth2/auth?" +
                    "client_id=" + googleClientId + "&" +
                    "redirect_uri=" + java.net.URLEncoder.encode(googleRedirectUri, "UTF-8") + "&" +
                    "response_type=code&" +
                    "scope=" + java.net.URLEncoder
                            .encode("openid profile email https://www.googleapis.com/auth/calendar.events", "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("URLエンコーディングに失敗しました: {}", e.getMessage());
            return null;
        }
    }
}