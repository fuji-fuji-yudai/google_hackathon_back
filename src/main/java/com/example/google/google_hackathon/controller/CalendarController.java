package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.CalendarEventRequest;
import com.example.google.google_hackathon.service.GoogleCalendarService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/calendar") // 新しいエンドポイントパス
public class CalendarController {

    @Autowired
    private GoogleCalendarService googleCalendarService;

    // Google Calendarにイベントを作成するエンドポイント
    @PostMapping("/events")
    public ResponseEntity<String> createCalendarEvent(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @RequestBody CalendarEventRequest eventRequest) { // リクエストボディでイベント情報を受け取る
        try {
            // 文字列からLocalDateTimeに変換
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime startDateTime = LocalDateTime.parse(eventRequest.getStartDateTimeStr(), formatter);
            LocalDateTime endDateTime = LocalDateTime.parse(eventRequest.getEndDateTimeStr(), formatter);

            JsonNode response = googleCalendarService.createGoogleCalendarEvent(
                    authorizedClient,
                    eventRequest.getSummary(),
                    eventRequest.getDescription(),
                    startDateTime,
                    endDateTime,
                    eventRequest.getTimeZone());

            if (response != null) {
                return ResponseEntity.ok("Event created: " + response.get("htmlLink").asText());
            } else {
                return ResponseEntity.status(500).body("Failed to create calendar event.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error creating event: " + e.getMessage());
        }
    }

    // Google Calendarのイベントをリストするエンドポイント
    @GetMapping("/events")
    public ResponseEntity<JsonNode> listCalendarEvents(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @RequestParam(defaultValue = "2025-01-01T00:00") String timeMinStr,
            @RequestParam(defaultValue = "2025-12-31T23:59") String timeMaxStr,
            @RequestParam(defaultValue = "Asia/Tokyo") String timeZone) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime timeMin = LocalDateTime.parse(timeMinStr, formatter);
            LocalDateTime timeMax = LocalDateTime.parse(timeMaxStr, formatter);

            JsonNode events = googleCalendarService.listGoogleCalendarEvents(
                    authorizedClient, timeMin, timeMax, timeZone);

            if (events != null) {
                return ResponseEntity.ok(events);
            } else {
                return ResponseEntity.status(500).body(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }
}