package com.example.google.google_hackathon.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    @Value("${google.service-account.secret-id}")
    private String serviceAccountSecretId;

    @Value("${google.service-account.user-email}")
    private String serviceAccountUserEmail;

    private final ObjectMapper objectMapper;

    public GoogleCalendarService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private Calendar getCalendarService(String userEmailToImpersonate) throws IOException, GeneralSecurityException {
        logger.info("サービスアカウントでGoogle Calendarサービスを初期化中。委任ユーザー: {}", userEmailToImpersonate);

        InputStream keyStream = null;
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.parse(serviceAccountSecretId + "/versions/latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

            byte[] keyBytes = response.getPayload().getData().toByteArray();
            keyStream = new ByteArrayInputStream(keyBytes);

            GoogleCredential credential = GoogleCredential.fromStream(keyStream)
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR))
                    .createDelegated(userEmailToImpersonate);

            return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName("Google Hackathon Application")
                    .build();

        } catch (Exception e) {
            logger.error("Secret Managerからのサービスアカウントキー取得中にエラーが発生しました: {}", e.getMessage(), e);
            throw new IOException("Failed to retrieve service account key from Secret Manager", e);
        } finally {
            if (keyStream != null) {
                keyStream.close();
            }
        }
    }

    public JsonNode createGoogleCalendarEvent(
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String timeZone,
            List<String> attendeeEmails) throws IOException, GeneralSecurityException {

        logger.info("Googleカレンダーイベント作成を開始します。タイトル: {}, 開始: {}, 終了: {}, 参加者数: {}",
                title, startDateTime, endDateTime, attendeeEmails != null ? attendeeEmails.size() : 0);

        Calendar service = getCalendarService(serviceAccountUserEmail);

        Event event = new Event()
                .setSummary(title)
                .setDescription(description);

        List<EventAttendee> attendees = new ArrayList<>();
        attendees.add(new EventAttendee().setEmail(serviceAccountUserEmail));

        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            for (String email : attendeeEmails) {
                String trimmedEmail = email.trim();
                if (!trimmedEmail.isEmpty() && !trimmedEmail.equalsIgnoreCase(serviceAccountUserEmail)) {
                    attendees.add(new EventAttendee().setEmail(trimmedEmail));
                }
            }
        }
        event.setAttendees(attendees);

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        Date.from(startDateTime.atZone(ZoneId.of(timeZone)).toInstant())))
                .setTimeZone(timeZone);
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        Date.from(endDateTime.atZone(ZoneId.of(timeZone)).toInstant())))
                .setTimeZone(timeZone);
        event.setEnd(end);

        String calendarId = "primary";

        try {
            Event createdEvent = service.events().insert(calendarId, event).setSendNotifications(true).execute();
            logger.info("Google Calendar Event created. HTML Link: {}", createdEvent.getHtmlLink());
            if (createdEvent.getAttendees() != null) {
                createdEvent.getAttendees()
                        .forEach(a -> logger.info("Attendee: {} - Status: {}", a.getEmail(), a.getResponseStatus()));
            }

            ObjectNode jsonResponse = objectMapper.createObjectNode();
            jsonResponse.put("id", createdEvent.getId());
            jsonResponse.put("htmlLink", createdEvent.getHtmlLink());
            jsonResponse.put("status", createdEvent.getStatus());
            return jsonResponse;

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            logger.error("Google Calendar API Error: Status Code={}, Message={}", e.getStatusCode(),
                    e.getDetails().getMessage(), e);
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                throw new IOException("Googleサービスアカウントの認証に失敗しました。キーファイルとドメイン全体の委任設定を確認してください。", e);
            }
            throw new IOException("Google Calendar APIリクエストが失敗しました: " + e.getDetails().getMessage(), e);
        } catch (Exception e) {
            logger.error("Google Calendarイベント作成中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
            throw e;
        }
    }
}