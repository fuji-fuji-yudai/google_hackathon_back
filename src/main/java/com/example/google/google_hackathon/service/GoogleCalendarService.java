package com.example.google.google_hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

// Google Calendar API を直接呼び出すサービス
@Service
public class GoogleCalendarService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // JSON処理用

    @Autowired
    public GoogleCalendarService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ★新規追加: Google Calendarにイベントを作成するメソッド
    public JsonNode createGoogleCalendarEvent(
            OAuth2AuthorizedClient authorizedClient,
            String summary,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String timeZone) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        String calendarApiUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        // 日時をRFC3339形式にフォーマット (例: 2023-10-26T10:00:00+09:00)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        ZonedDateTime zonedStart = ZonedDateTime.of(startDateTime, ZoneId.of(timeZone));
        ZonedDateTime zonedEnd = ZonedDateTime.of(endDateTime, ZoneId.of(timeZone));

        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("summary", summary);
        if (description != null && !description.isEmpty()) {
            eventNode.put("description", description);
        }

        ObjectNode startNode = objectMapper.createObjectNode();
        startNode.put("dateTime", zonedStart.format(formatter));
        startNode.put("timeZone", timeZone);
        eventNode.set("start", startNode);

        ObjectNode endNode = objectMapper.createObjectNode();
        endNode.put("dateTime", zonedEnd.format(formatter));
        endNode.put("timeZone", timeZone);
        eventNode.set("end", endNode);

        HttpEntity<String> request = new HttpEntity<>(eventNode.toString(), headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    calendarApiUrl,
                    HttpMethod.POST,
                    request,
                    JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println(
                        "Google Calendar Event created successfully: " + response.getBody().get("htmlLink").asText());
                return response.getBody();
            } else {
                System.err.println("Failed to create Google Calendar event. Status: " + response.getStatusCode());
                System.err.println("Response body: " + response.getBody());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error creating Google Calendar event: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ★新規追加: Google Calendarのイベントをリストするメソッド
    public JsonNode listGoogleCalendarEvents(
            OAuth2AuthorizedClient authorizedClient,
            LocalDateTime timeMin,
            LocalDateTime timeMax,
            String timeZone) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // RFC3339形式にフォーマット
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        ZonedDateTime zonedTimeMin = ZonedDateTime.of(timeMin, ZoneId.of(timeZone));
        ZonedDateTime zonedTimeMax = ZonedDateTime.of(timeMax, ZoneId.of(timeZone));

        String calendarApiUrl = String.format(
                "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=%s&timeMax=%s&singleEvents=true&orderBy=startTime",
                zonedTimeMin.format(formatter),
                zonedTimeMax.format(formatter));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    calendarApiUrl,
                    HttpMethod.GET,
                    request,
                    JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Google Calendar Events listed successfully.");
                JsonNode items = response.getBody().get("items");
                if (items != null && items.isArray()) {
                    items.forEach(item -> {
                        System.out.printf("- %s (%s)\n", item.get("summary").asText(),
                                item.get("start").get("dateTime").asText());
                    });
                }
                return response.getBody();
            } else {
                System.err.println("Failed to list Google Calendar events. Status: " + response.getStatusCode());
                System.err.println("Response body: " + response.getBody());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error listing Google Calendar events: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}