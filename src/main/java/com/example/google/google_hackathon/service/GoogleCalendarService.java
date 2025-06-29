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

    // サービスアカウントキーが格納されているシークレットのID (Cloud Runの環境変数名に合わせる)
    // @Value("${google.service-account.secret-id}")
    @Value("calendar-service-account-key")
    private String serviceAccountSecretId;

    // 委任ユーザーのメールアドレス (Cloud Runの環境変数名に合わせ、Secret Managerを経由しないので直接値を受け取る)
    @Value("mk.mihokoyama@gmail.com")
    //@Value("${GOOGLECALENDAR_SERVICE_ACCOUNT_USER_EMAIL}")

    private String delegatedUserEmail;

    private final ObjectMapper objectMapper;
    private byte[] serviceAccountKeyBytes; // サービスアカウントキーのバイト配列

    public GoogleCalendarService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Beanの初期化時に、サービスアカウントキーをSecret Managerから取得し、メモリにロードします。
     * 委任ユーザーのメールアドレスは@Valueで直接注入されるため、ここでは取得しません。
     */
    @PostConstruct
    public void init() throws IOException {
        // @Valueによって注入された serviceAccountSecretId の値を確認
        logger.info("serviceAccountSecretId (from @Value): {}", serviceAccountSecretId);
        logger.info("delegatedUserEmail (from @Value): {}", delegatedUserEmail);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            logger.info("サービスアカウントキーをSecret Managerから取得中。Secret ID: {}", serviceAccountSecretId);

            // 重要: ここに記載されているプロジェクトIDが、実際にCloud Runがデプロイされている
            // Google Cloud プロジェクトのIDと完全に一致しているか、**再度、慎重に確認してください**。
            // スペースや誤字脱字がないか、GCPコンソールで確認することをお勧めします。
            String projectId = "nomadic-bison-459812-a8"; // あなたのプロジェクトID

            SecretVersionName serviceAccountKeyName = SecretVersionName.newBuilder()
                    .setProject(projectId)
                    .setSecret(serviceAccountSecretId)
                    .setSecretVersion("latest")
                    .build();

            // 構築されたSecret Version Nameの文字列を確認
            // logger.info("Constructed Secret Version Name: {}",
            // serviceAccountKeyName.toString());

            AccessSecretVersionResponse keyResponse = client.accessSecretVersion(serviceAccountKeyName);
            this.serviceAccountKeyBytes = keyResponse.getPayload().getData().toByteArray();
            logger.info("サービスアカウントキーを正常に取得しました。");

        } catch (Exception e) {
            logger.error("Secret Managerからの認証情報取得中にエラーが発生しました: {}", e.getMessage(), e);
            // エラーの詳細をログに出力し、原因究明を助ける
            if (e.getCause() instanceof java.util.IllegalFormatWidthException) {
                logger.error(
                        "詳細: IllegalFormatWidthExceptionが発生しました。これは通常、Secret Managerのパス構築（プロジェクトIDやシークレット名）に問題があることを示唆しています。",
                        e.getCause());
            }
            throw new IOException("Failed to retrieve credentials from Secret Manager", e);
        }
    }

    private Calendar getCalendarService(String userEmailToImpersonate) throws IOException, GeneralSecurityException {
        logger.info("Google Calendarサービスを初期化中。委任ユーザー: {}", userEmailToImpersonate);

        // サービスアカウントキーのバイト配列がnullでないことを確認
        if (serviceAccountKeyBytes == null) {
            throw new IllegalStateException("Service account key bytes not loaded. init() method might have failed.");
        }

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

        Calendar service = getCalendarService(delegatedUserEmail);

        Event event = new Event()
                .setSummary(title)
                .setDescription(description);

        List<EventAttendee> attendees = new ArrayList<>();
        // 委任ユーザー自身も参加者として追加
        attendees.add(new EventAttendee().setEmail(delegatedUserEmail));

        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            for (String email : attendeeEmails) {
                String trimmedEmail = email.trim();
                // 委任ユーザーのメールアドレスが重複しないようにチェック
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

        String calendarId = "primary"; // サービスアカウントがアクセスできるメインカレンダー

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