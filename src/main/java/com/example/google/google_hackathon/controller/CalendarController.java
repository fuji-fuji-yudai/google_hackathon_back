package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.CalendarEventRequest;
import com.example.google.google_hackathon.service.GoogleCalendarService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // 認証情報を取得するために追加
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger; // ロギングを追加
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException; // 日時パースエラーのために追加

@RestController
@RequestMapping("/api/calendar") // 新しいエンドポイントパス
public class CalendarController {

        private static final Logger logger = LoggerFactory.getLogger(CalendarController.class);

        @Autowired
        private GoogleCalendarService googleCalendarService;

        // Google Calendarにイベントを作成するエンドポイント
        @PostMapping("/events")
        public ResponseEntity<String> createCalendarEvent(
                        // ★修正: @RegisteredOAuth2AuthorizedClient を削除し、Authentication を受け取るように変更
                        // JWT認証フローでは、アクセストークンはDBに保存されているため、ここで直接受け取らない
                        Authentication authentication,
                        @RequestBody CalendarEventRequest eventRequest) { // リクエストボディでイベント情報を受け取る
                logger.info("イベント作成リクエストを受け付けました。ユーザー: {}", authentication.getName());
                try {
                        // 文字列からLocalDateTimeに変換
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                        LocalDateTime startDateTime = LocalDateTime.parse(eventRequest.getStartDateTimeStr(),
                                        formatter);
                        LocalDateTime endDateTime = LocalDateTime.parse(eventRequest.getEndDateTimeStr(), formatter);

                        // ★修正: authorizedClient を渡す代わりに、authentication オブジェクトをサービスに渡す
                        // サービス側で appUser の ID を使って GoogleAuthToken から accessToken を取得する
                        JsonNode response = googleCalendarService.createGoogleCalendarEvent(
                                        authentication, // 認証情報全体を渡す
                                        eventRequest.getSummary(),
                                        eventRequest.getDescription(),
                                        startDateTime,
                                        endDateTime,
                                        eventRequest.getTimeZone());

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

        // Google Calendarのイベントをリストするエンドポイント
        @GetMapping("/events")
        public ResponseEntity<JsonNode> listCalendarEvents(
                        // ★修正: @RegisteredOAuth2AuthorizedClient を削除し、Authentication を受け取るように変更
                        Authentication authentication,
                        @RequestParam(defaultValue = "2025-01-01T00:00") String timeMinStr,
                        @RequestParam(defaultValue = "2025-12-31T23:59") String timeMaxStr,
                        @RequestParam(defaultValue = "Asia/Tokyo") String timeZone) {
                logger.info("イベント一覧リクエストを受け付けました。ユーザー: {}", authentication.getName());
                try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                        LocalDateTime timeMin = LocalDateTime.parse(timeMinStr, formatter);
                        LocalDateTime timeMax = LocalDateTime.parse(timeMaxStr, formatter);

                        // ★修正: authorizedClient を渡す代わりに、authentication オブジェクトをサービスに渡す
                        JsonNode events = googleCalendarService.listGoogleCalendarEvents(
                                        authentication, timeMin, timeMax, timeZone);

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