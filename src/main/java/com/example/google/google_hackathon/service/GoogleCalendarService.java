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
import jakarta.annotation.PostConstruct;

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
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    // サービスアカウントキーが格納されているシークレットのID
    @Value("${google.service-account.secret-id}")
    private String serviceAccountSecretId;

    // 委任ユーザーのメールアドレスが格納されているシークレットのID
    @Value("${google.delegate-email.secret-id}") // 新しいプロパティ名
    private String delegateEmailSecretId; // 新しいフィールド

    private final ObjectMapper objectMapper;
    private byte[] serviceAccountKeyBytes; // サービスアカウントキーのバイト配列
    private String delegatedUserEmail; // Secret Managerから取得する委任ユーザーのメールアドレス

    public GoogleCalendarService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Beanの初期化時に、サービスアカウントキーと委任ユーザーのメールアドレスを
     * それぞれのSecret Managerから取得し、メモリにロードします。
     */
    @PostConstruct
    public void init() throws IOException {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            // サービスアカウントキーの取得
            logger.info("サービスアカウントキーをSecret Managerから取得中。Secret ID: {}", serviceAccountSecretId);
            SecretVersionName serviceAccountKeyName = SecretVersionName.newBuilder()
                    .setProject("nomadic-bison-459812-a8") // あなたのプロジェクトIDに置き換えてください
                    .setSecret(serviceAccountSecretId)
                    .setSecretVersion("latest")
                    .build();
            AccessSecretVersionResponse keyResponse = client.accessSecretVersion(serviceAccountKeyName);
            this.serviceAccountKeyBytes = keyResponse.getPayload().getData().toByteArray();
            logger.info("サービスアカウントキーを正常に取得しました。");

            // 委任ユーザーのメールアドレスの取得
            logger.info("委任ユーザーのメールアドレスをSecret Managerから取得中。Secret ID: {}", delegateEmailSecretId);
            SecretVersionName delegateEmailName = SecretVersionName.newBuilder()
                    .setProject("nomadic-bison-459812-a8") // あなたのプロジェクトIDに置き換えてください
                    .setSecret(delegateEmailSecretId)
                    .setSecretVersion("latest")
                    .build();
            AccessSecretVersionResponse emailResponse = client.accessSecretVersion(delegateEmailName);
            this.delegatedUserEmail = emailResponse.getPayload().getData().toStringUtf8();
            logger.info("委任ユーザーのメールアドレスを正常に取得しました: {}", delegatedUserEmail);

        } catch (Exception e) {
            logger.error("Secret Managerからの認証情報取得中にエラーが発生しました: {}", e.getMessage(), e);
            throw new IOException("Failed to retrieve credentials from Secret Manager", e);
        }
    }

    private Calendar getCalendarService(String userEmailToImpersonate) throws IOException, GeneralSecurityException {
        logger.info("Google Calendarサービスを初期化中。委任ユーザー: {}", userEmailToImpersonate);

        InputStream keyStream = new ByteArrayInputStream(serviceAccountKeyBytes);

        GoogleCredential credential = GoogleCredential.fromStream(keyStream)
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR))
                .createDelegated(userEmailToImpersonate);

        return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("Google Hackathon Application")
                .build();
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

        Calendar service = getCalendarService(delegatedUserEmail); // Secret Managerから取得したメールアドレスを使用

        Event event = new Event()
                .setSummary(title)
                .setDescription(description);

        List<EventAttendee> attendees = new ArrayList<>();
        attendees.add(new EventAttendee().setEmail(delegatedUserEmail));

        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            for (String email : attendeeEmails) {
                String trimmedEmail = email.trim();
                if (!trimmedEmail.isEmpty() && !trimmedEmail.equalsIgnoreCase(delegatedUserEmail)) {
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
                logger.error("401/403エラー: サービスアカウントの認証失敗、またはドメイン全体の委任設定が不十分です。");
                throw new IOException("Googleサービスアカウントの認証に失敗しました。キーファイルとドメイン全体の委任設定を確認してください。", e);
            }
            throw new IOException("Google Calendar APIリクエストが失敗しました: " + e.getDetails().getMessage(), e);
        } catch (Exception e) {
            logger.error("Google Calendarイベント作成中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
            throw e;
        }
    }
}