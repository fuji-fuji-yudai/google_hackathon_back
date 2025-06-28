package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser; // AppUserを使うために必要
import com.example.google.google_hackathon.entity.GoogleAuthToken; // GoogleAuthTokenを使うために必要
import com.example.google.google_hackathon.repository.AppUserRepository; // AppUserを探すために必要
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository; // GoogleAuthTokenを探すために必要
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // JsonNodeを扱うために必要
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken; // BearerTokenCredentialBuilder の代わりに BearerToken を使用
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.EventDateTime; // イベント日時設定のために必要
import org.springframework.security.core.Authentication; // 認証情報を取得するために必要
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken; // OAuth2AuthenticationToken を扱うために必要
import org.springframework.security.oauth2.core.user.OAuth2User; // OAuth2User を扱うために必要
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime; // タイムゾーン付きの日時を扱うために必要
import java.time.Instant; // 日時変換のために必要

/**
 * Google Calendar API と連携するためのサービスです。
 * データベースに保存されたアクセストークンを使用して、Googleカレンダーのイベントを操作します。
 */
@Service
public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance(); // JSON処理用

    private final AppUserRepository appUserRepository; // AppUserを探す道具だよ
    private final GoogleAuthTokenRepository googleAuthTokenRepository; // GoogleAuthTokenを探す道具だよ
    private final ObjectMapper objectMapper; // JSONをいい感じに扱う道具だよ

    // コンストラクタ: 必要な道具をもらうよ
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
        // これからGoogleカレンダーを使うための「鍵」を準備するよ

        // 認証情報がGoogleログインのものか確認
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            logger.error("AuthenticationオブジェクトがOAuth2AuthenticationTokenではありません。JWT認証または他の認証タイプが想定されています。");
            throw new IllegalStateException("Google OAuth2認証ではないため、処理できません。");
        }

        // ログインしているGoogleユーザーのID（番号）をもらうよ
        String googleSubId = ((OAuth2User) authentication.getPrincipal()).getName();
        logger.debug("Credential取得中。Google Sub ID: {}", googleSubId);

        // GoogleのIDから、Googleのログイン情報（GoogleAuthToken）を見つけるよ。
        // これがないと、どのAppUserに紐づくか分からないし、アクセストークンも取れないよ。
        GoogleAuthToken googleAuthToken = googleAuthTokenRepository.findByGoogleSubId(googleSubId)
                .orElseThrow(() -> {
                    logger.error("重大なデータ不整合: GoogleAuthTokenが見つからない。Google ID: {}", googleSubId);
                    return new IllegalStateException("Googleログイン情報が見つかりません。再ログインを試してください。");
                });

        // データベースからアクセストークンをもらうよ
        String accessToken = googleAuthToken.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            logger.error("アクセストークンがGoogleAuthToken (ID: {}) から見つからない、または空です。", googleAuthToken.getId());
            throw new IllegalStateException("Googleアクセストークンが見つかりません。");
        }
        logger.debug("アクセストークンを取得しました。");

        // このアクセストークンを使って、Google APIに「これは本物だよ！」って伝えるためのCredentialを作るよ
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
     */
    public JsonNode createGoogleCalendarEvent(
            Authentication authentication, // ★修正: authentication を受け取るようにしたよ
            String summary, String description,
            LocalDateTime startDateTime, LocalDateTime endDateTime, String timeZone)
            throws IOException, GeneralSecurityException {
        logger.info("Googleカレンダーイベント作成を開始します。Summary: {}", summary);

        // Google APIを使うためのCredentialをもらうよ
        Credential credential = getCredential(authentication);

        // Google Calendar と話すための特別な道具（サービス）を作るよ
        Calendar service = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), // 安全な通信方法を使うよ
                JSON_FACTORY, // JSONを扱う道具だよ
                credential) // 秘密の鍵（Credential）を使うよ
                .setApplicationName("Google Hackathon App") // あなたのアプリの名前だよ
                .build();

        // イベントの開始日時と終了日時を設定するよ
        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        ZonedDateTime.of(startDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli()))
                .setTimeZone(timeZone);
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        ZonedDateTime.of(endDateTime, ZoneId.of(timeZone)).toInstant().toEpochMilli()))
                .setTimeZone(timeZone);

        // イベントの情報を作るよ
        Event event = new Event()
                .setSummary(summary)
                .setDescription(description)
                .setStart(start)
                .setEnd(end);

        // イベントをカレンダーに追加するよ（"primary"は自分のメインカレンダーだよ）
        String calendarId = "primary";
        Event createdEvent = service.events().insert(calendarId, event).execute();

        logger.info("Googleカレンダーイベントを正常に作成しました。ID: {}", createdEvent.getId());
        return objectMapper.convertValue(createdEvent, JsonNode.class); // 作ったイベントの情報をJSONで返すよ
    }

    /**
     * Google Calendar のイベント一覧をもらうよ。
     *
     * @param authentication ログインしている人の情報だよ
     * @param timeMin        イベントの開始日時（これ以降）だよ
     * @param timeMax        イベントの終了日時（これ以前）だよ
     * @param timeZone       タイムゾーンだよ
     * @return イベント一覧の情報（JSON形式）だよ
     * @throws IOException              通信エラーがあったら
     * @throws GeneralSecurityException 認証やセキュリティのエラーがあったら
     */
    public JsonNode listGoogleCalendarEvents(
            Authentication authentication, // ★修正: authentication を受け取るようにしたよ
            LocalDateTime timeMin, LocalDateTime timeMax, String timeZone)
            throws IOException, GeneralSecurityException {
        logger.info("Googleカレンダーイベント一覧取得を開始します。TimeMin: {}, TimeMax: {}", timeMin, timeMax);

        // Google APIを使うためのCredentialをもらうよ
        Credential credential = getCredential(authentication);

        // Google Calendar と話すための特別な道具（サービス）を作るよ
        Calendar service = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName("Google Hackathon App")
                .build();

        // イベントを探す期間を設定するよ
        // LocalDateTimeをZonedDateTimeに変換し、Instantを経由してcom.google.api.client.util.DateTimeに変換
        com.google.api.client.util.DateTime minDateTime = new com.google.api.client.util.DateTime(
                ZonedDateTime.of(timeMin, ZoneId.of(timeZone)).toInstant().toEpochMilli());
        com.google.api.client.util.DateTime maxDateTime = new com.google.api.client.util.DateTime(
                ZonedDateTime.of(timeMax, ZoneId.of(timeZone)).toInstant().toEpochMilli());

        // カレンダーからイベントを探してきてもらうよ
        String calendarId = "primary";
        Events events = service.events().list(calendarId)
                .setTimeMin(minDateTime) // この日時から
                .setTimeMax(maxDateTime) // この日時まで
                .setSingleEvents(true) // 繰り返しイベントは一つずつ表示するよ
                .setOrderBy("startTime") // 始まる時間順に並べるよ
                .setQ("event") // 'event'という単語を含むイベントを検索する例
                .execute();

        logger.info("Googleカレンダーイベント一覧取得成功。{} 件のイベントが見つかりました。",
                events.getItems() != null ? events.getItems().size() : 0);
        return objectMapper.convertValue(events, JsonNode.class); // 見つかったイベントの情報をJSONで返すよ
    }
}