package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.googleapis.json.GoogleJsonResponseException; // ★追加: GoogleJsonResponseException をインポート
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.sql.Timestamp; // Timestamp クラスの import を追加 (LocalDateTime -> DateTime 変換用)

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

    // コンストラクタ
    public GoogleCalendarService(AppUserRepository appUserRepository,
            GoogleAuthTokenRepository googleAuthTokenRepository, ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 認証情報から Google Calendar API を使うための Credential を作って返すよ。
     *
     * @param authentication 認証情報
     * @return Google Calendar API を使うための Credential
     * @throws IllegalStateException ユーザーやトークンが見つからない場合
     */
    private Credential getCredential(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            logger.error("AuthenticationオブジェクトがOAuth2AuthenticationTokenではありません。JWT認証または他の認証タイプが想定されています。");
            throw new IllegalStateException("Google OAuth2認証ではないため、処理できません。");
        }

        String googleSubId = ((OAuth2User) authentication.getPrincipal()).getName();
        logger.debug("Credential取得中。Google Sub ID: {}", googleSubId);

        GoogleAuthToken googleAuthToken = googleAuthTokenRepository.findByGoogleSubId(googleSubId)
                .orElseThrow(() -> {
                    logger.error("重大なデータ不整合: GoogleAuthTokenが見つからない。Google ID: {}", googleSubId);
                    return new IllegalStateException("Googleログイン情報が見つかりません。再ログインを試してください。");
                });

        String accessToken = googleAuthToken.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            logger.error("アクセストークンがGoogleAuthToken (ID: {}) から見つからない、または空です。", googleAuthToken.getId());
            throw new IllegalStateException("Googleアクセストークンが見つかりません。");
        }
        logger.debug("アクセストークンを取得しました。");

        return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);
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
            throws IOException, GeneralSecurityException, GoogleAuthException { // ★修正: GoogleAuthException を追加
        logger.info("Googleカレンダーイベント作成を開始します。Summary: {}", summary);

        try { // ★追加: API呼び出し全体をtry-catchで囲む
            Credential credential = getCredential(authentication);

            Calendar service = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential)
                    .setApplicationName("Google Hackathon App")
                    .build();

            // LocalDateTime を com.google.api.client.util.DateTime に変換
            // ZoneId.of(timeZone) を使って ZonedDateTime を経由し、Instant からミリ秒を取得
            com.google.api.client.util.DateTime start = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(startDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli());
            com.google.api.client.util.DateTime end = new com.google.api.client.util.DateTime(
                    ZonedDateTime.of(endDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli());

            Event event = new Event()
                    .setSummary(summary)
                    .setDescription(description)
                    .setStart(new EventDateTime().setDateTime(start).setTimeZone(timeZone)) // EventDateTime の設定
                    .setEnd(new EventDateTime().setDateTime(end).setTimeZone(timeZone)); // EventDateTime の設定

            String calendarId = "primary";
            logger.info("Google Calendar API を呼び出し中... カレンダーID: {}, イベント概要: {}", calendarId, summary); // ★追加ログ
            Event createdEvent = service.events().insert(calendarId, event).execute();
            logger.info("Google Calendar API 呼び出し完了。Created Event ID: {}", createdEvent.getId()); // ★追加ログ

            return objectMapper.convertValue(createdEvent, JsonNode.class);

        } catch (GoogleJsonResponseException e) { // ★追加: Google APIからのJSONレスポンスエラーをキャッチ
            logger.error("Google Calendar API エラーレスポンス (JSON): {}", e.getDetails().getMessage(), e);
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                // 認証エラーの場合、GoogleAuthExceptionをスローしてControllerでリダイレクトを処理
                logger.warn("Google Calendar APIからの認証/権限エラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage());
                // getGoogleAuthUrl() は、別途、認証フローを開始するためのURLを返すように実装されている必要があります。
                // 通常は、OAuth2クライアントの認可URIを生成する処理になります。
                throw new GoogleAuthException("Google Calendar APIへのアクセスに認証が必要です", getGoogleAuthUrl());
            } else {
                // その他のGoogle APIエラーはそのままIOExceptionとしてスロー
                logger.error("Google Calendar APIからの予期せぬエラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage(),
                        e);
                throw new IOException("Google Calendar APIエラー: " + e.getDetails().getMessage(), e);
            }
        } catch (IOException e) {
            logger.error("Google Calendar API呼び出し中のI/Oエラー: {}", e.getMessage(), e); // ★追加ログ
            throw e;
        } catch (GeneralSecurityException e) {
            logger.error("Google Calendar API認証情報のセキュリティエラー: {}", e.getMessage(), e); // ★追加ログ
            throw e;
        } catch (IllegalStateException e) { // getCredentialで発生するIllegalStateExceptionをキャッチ
            logger.error("Credential取得エラー: {}", e.getMessage(), e);
            // ここでGoogleAuthExceptionをスローして、フロントエンドに認証を促す
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
            throws IOException, GeneralSecurityException, GoogleAuthException { // ★修正: GoogleAuthException を追加
        logger.info("Googleカレンダーイベント一覧取得を開始します。TimeMin: {}, TimeMax: {}", timeMin, timeMax);

        try { // ★追加: API呼び出し全体をtry-catchで囲む
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
            logger.info("Google Calendar API (List) を呼び出し中... カレンダーID: {}", calendarId); // ★追加ログ
            Events events = service.events().list(calendarId)
                    .setTimeMin(minDateTime)
                    .setTimeMax(maxDateTime)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setQ("event") // 'event'という単語を含むイベントを検索する例
                    .execute();
            logger.info("Google Calendar API (List) 呼び出し完了。{} 件のイベントが見つかりました。",
                    events.getItems() != null ? events.getItems().size() : 0); // ★追加ログ

            return objectMapper.convertValue(events, JsonNode.class);

        } catch (GoogleJsonResponseException e) { // ★追加: Google APIからのJSONレスポンスエラーをキャッチ
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
            logger.error("Google Calendar API呼び出し中のI/Oエラー: {}", e.getMessage(), e); // ★追加ログ
            throw e;
        } catch (GeneralSecurityException e) {
            logger.error("Google Calendar API認証情報のセキュリティエラー: {}", e.getMessage(), e); // ★追加ログ
            throw e;
        } catch (IllegalStateException e) { // getCredentialで発生するIllegalStateExceptionをキャッチ
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

        final String CLIENT_ID = "14467698004-st2mnmp5t5ebt3nbj1kkgvamj7f5jps5.apps.googleusercontent.com";
        final String REDIRECT_URI = "https://my-image-14467698004.asia-northeast1.run.app/oauth2/callback";
        final String SCOPE = "openid profile email https://www.googleapis.com/auth/calendar.events";

        try {
            return "https://accounts.google.com/o/oauth2/auth?" +
                    "client_id=" + CLIENT_ID + "&" +
                    "redirect_uri=" + java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8") + "&" + // ここはURLエンコードする
                    "response_type=code&" +
                    "scope=" + java.net.URLEncoder.encode(SCOPE, "UTF-8"); // ここもURLエンコードする
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("URLエンコーディングに失敗しました: {}", e.getMessage());
            return null; // または適切なエラーハンドリング
        }
    }
}