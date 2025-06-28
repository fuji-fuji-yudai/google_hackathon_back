package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.CalendarEventRequest;
import com.example.google.google_hackathon.service.GoogleCalendarService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

        private static final Logger logger = LoggerFactory.getLogger(CalendarController.class);

        @Autowired
        private GoogleCalendarService googleCalendarService;

        /**
         * Google Calendarにイベントを作成するエンドポイント
         * フロントエンドからGoogleのアクセストークンを直接受け取ります。
         *
         * @param authentication    JWT認証情報（ロギングなど、アプリケーション独自の認証が必要な場合のみ使用）
         * @param googleAccessToken Google APIにアクセスするためのアクセストークン
         * @param eventRequest      イベントの詳細情報
         * @return イベント作成の結果
         */
        @PostMapping("/events")
        public ResponseEntity<String> createCalendarEvent(
                        Authentication authentication, // アプリケーション独自のJWT認証情報（ログ目的などで残す）
                        @RequestHeader("X-Google-Access-Token") String googleAccessToken, // Googleのアクセストークンをヘッダーから受け取る
                        @RequestBody CalendarEventRequest eventRequest) {

                logger.info("イベント作成リクエストを受け付けました。ユーザー: {}。Googleアクセストークン提供済み。",
                                authentication != null ? authentication.getName() : "匿名");

                try {
                        // CalendarEventRequestのゲッターが存在すると仮定
                        // 文字列からLocalDateTimeに変換
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                        LocalDateTime startDateTime = LocalDateTime.parse(eventRequest.getStartDateTimeStr(), // ★修正:
                                                                                                              // getStartDateTimeStr()
                                        formatter);
                        LocalDateTime endDateTime = LocalDateTime.parse(eventRequest.getEndDateTimeStr(), formatter); // ★修正:
                                                                                                                      // getEndDateTimeStr()

                        // GoogleCalendarServiceにGoogleアクセストークン文字列を直接渡す
                        JsonNode response = googleCalendarService.createGoogleCalendarEvent(
                                        googleAccessToken, // Googleのアクセストークンを直接渡す
                                        eventRequest.getSummary(), // ★修正: getSummary()
                                        eventRequest.getDescription(), // ★修正: getDescription()
                                        startDateTime,
                                        endDateTime,
                                        eventRequest.getTimeZone()); // ★修正: getTimeZone()

                        if (response != null && response.has("htmlLink")) {
                                logger.info("イベント作成成功: {}", response.get("htmlLink").asText());
                                return ResponseEntity.ok("Event created: " + response.get("htmlLink").asText());
                        } else {
                                logger.error("イベント作成に失敗しました。応答が不正です。");
                                return ResponseEntity.status(500)
                                                .body("Failed to create calendar event. Invalid response.");
                        }
                } catch (DateTimeParseException e) {
                        logger.error("日付時刻のパースエラー: {}", e.getMessage(), e);
                        return ResponseEntity.status(400).body("Error parsing date/time: " + e.getMessage());
                } catch (Exception e) {
                        logger.error("イベント作成中にエラーが発生しました。", e);
                        return ResponseEntity.status(500).body("Error creating event: " + e.getMessage());
                }
        }

        /**
         * Google Calendarのイベントをリストするエンドポイント
         * フロントエンドからGoogleのアクセストークンを直接受け取ります。
         *
         * @param authentication    JWT認証情報（ロギングなど、アプリケーション独自の認証が必要な場合のみ使用）
         * @param googleAccessToken Google APIにアクセスするためのアクセストークン
         * @param timeMinStr        期間の開始時刻文字列
         * @param timeMaxStr        期間の終了時刻文字列
         * @param timeZone          タイムゾーン
         * @return イベントリスト
         */
        @GetMapping("/events")
        public ResponseEntity<JsonNode> listCalendarEvents(
                        Authentication authentication, // アプリケーション独自のJWT認証情報（ログ目的などで残す）
                        @RequestHeader("X-Google-Access-Token") String googleAccessToken, // Googleのアクセストークンをヘッダーから受け取る
                        @RequestParam(defaultValue = "2025-01-01T00:00") String timeMinStr,
                        @RequestParam(defaultValue = "2025-12-31T23:59") String timeMaxStr,
                        @RequestParam(defaultValue = "Asia/Tokyo") String timeZone) {

                logger.info("イベント一覧リクエストを受け付けました。ユーザー: {}。Googleアクセストークン提供済み。",
                                authentication != null ? authentication.getName() : "匿名");

                try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                        LocalDateTime timeMin = LocalDateTime.parse(timeMinStr, formatter);
                        LocalDateTime timeMax = LocalDateTime.parse(timeMaxStr, formatter);

                        // GoogleCalendarServiceにGoogleアクセストークン文字列を直接渡す
                        JsonNode events = googleCalendarService.listGoogleCalendarEvents(
                                        googleAccessToken, timeMin, timeMax, timeZone); // Googleのアクセストークンを直接渡す

                        if (events != null) {
                                logger.info("イベント一覧取得成功。");
                                return ResponseEntity.ok(events);
                        } else {
                                logger.error("イベント一覧取得に失敗しました。応答が不正です。");
                                return ResponseEntity.status(500).body(null);
                        }
                } catch (DateTimeParseException e) {
                        logger.error("日付時刻のパースエラー: {}", e.getMessage(), e);
                        return ResponseEntity.status(400).body(null);
                } catch (Exception e) {
                        logger.error("イベント一覧取得中にエラーが発生しました。", e);
                        return ResponseEntity.status(500).body(null);
                }
        }
}