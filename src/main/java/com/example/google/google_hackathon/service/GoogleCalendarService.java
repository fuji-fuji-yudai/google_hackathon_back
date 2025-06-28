package com.example.google.google_hackathon.service;

// データベース関連のインポートは不要になります
// import com.example.google.google_hackathon.entity.GoogleAuthToken;
// import com.example.google.google_hackathon.repository.AppUserRepository;
// import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential; // GoogleCredentialを使用
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

// Authentication はコントローラーから渡されても、このサービスでは直接使用しない
// import org.springframework.security.core.Authentication; 
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections; // Collections.singletonList()用

/**
 * Google Calendar API と連携するためのサービスです。
 * フロントエンドから提供される一時的なアクセストークンを使用して、Googleカレンダーのイベントを操作します。
 * アクセストークンやリフレッシュトークンはデータベースに保存しません。
 */
@Service
public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final ObjectMapper objectMapper;

    // コンストラクタインジェクション (ObjectMapperのみ)
    public GoogleCalendarService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Google Calendar に新しいイベントを作成します。
     * アクセストークンは呼び出し元 (コントローラー) から直接渡されます。
     *
     * @param googleAccessToken Google API にアクセスするためのアクセストークン
     * @param summary           イベントの短い説明
     * @param description       イベントの詳しい説明
     * @param startDateTime     イベントが始まる日時
     * @param endDateTime       イベントが終わる日時
     * @param timeZone          イベントのタイムゾーン (例: "Asia/Tokyo")
     * @return 作成されたイベントの情報 (JSON形式)
     * @throws IOException              通信エラーがあった場合
     * @throws GeneralSecurityException セキュリティ関連のエラーがあった場合
     * @throws GoogleAuthException      認証が必要な場合 (アクセストークンが無効な場合など)
     */
    public JsonNode createGoogleCalendarEvent(
            String googleAccessToken, // ★修正: Authentication から String に変更
            String summary, String description,
            LocalDateTime startDateTime, LocalDateTime endDateTime, String timeZone)
            throws IOException, GeneralSecurityException, GoogleAuthException {
        logger.info("Googleカレンダーイベント作成を開始します。Summary: {}", summary);

        try {
            // ★修正: 受け取ったアクセストークンを使ってCredentialを直接構築
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JSON_FACTORY)
                    .build()
                    .setAccessToken(googleAccessToken); // フロントエンドから渡されたアクセストークンを設定

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
                // フロントエンドに再認証を促すエラーを投げる
                throw new GoogleAuthException("Google Calendar APIへのアクセスに認証が必要です。再ログインしてください。", null);
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
        }
    }

    /**
     * Google Calendar のイベント一覧を取得します。
     * アクセストークンは呼び出し元 (コントローラー) から直接渡されます。
     *
     * @param googleAccessToken Google API にアクセスするためのアクセストークン
     * @param timeMin           イベントの開始日時（これ以降）
     * @param timeMax           イベントの終了日時（これ以前）
     * @param timeZone          タイムゾーン
     * @return イベント一覧の情報 (JSON形式)
     * @throws IOException              通信エラーがあった場合
     * @throws GeneralSecurityException セキュリティ関連のエラーがあった場合
     * @throws GoogleAuthException      認証が必要な場合 (アクセストークンが無効な場合など)
     */
    public JsonNode listGoogleCalendarEvents(
            String googleAccessToken, // ★修正: Authentication から String に変更
            LocalDateTime timeMin, LocalDateTime timeMax, String timeZone)
            throws IOException, GeneralSecurityException, GoogleAuthException {
        logger.info("Googleカレンダーイベント一覧取得を開始します。TimeMin: {}, TimeMax: {}", timeMin, timeMax);

        try {
            // ★修正: 受け取ったアクセストークンを使ってCredentialを直接構築
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JSON_FACTORY)
                    .build()
                    .setAccessToken(googleAccessToken); // フロントエンドから渡されたアクセストークンを設定

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
                    // .setQ("event") // 特定のクエリが必要なければ削除
                    .execute();
            logger.info("Google Calendar API (List) 呼び出し完了。{} 件のイベントが見つかりました。",
                    events.getItems() != null ? events.getItems().size() : 0);

            return objectMapper.convertValue(events, JsonNode.class);

        } catch (GoogleJsonResponseException e) {
            logger.error("Google Calendar API エラーレスポンス (JSON): {}", e.getDetails().getMessage(), e);
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                logger.warn("Google Calendar APIからの認証/権限エラー (HTTP Status {}): {}", e.getStatusCode(), e.getMessage());
                // フロントエンドに再認証を促すエラーを投げる
                throw new GoogleAuthException("Google Calendar APIへのアクセスに認証が必要です。再ログインしてください。", null);
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
        }

    }

}