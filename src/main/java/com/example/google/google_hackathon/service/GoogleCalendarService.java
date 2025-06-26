package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.repository.ReminderRepository;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.client.util.DateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class GoogleCalendarService {

    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final ReminderRepository reminderRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    public GoogleCalendarService(GoogleAuthTokenRepository googleAuthTokenRepository,
            ReminderRepository reminderRepository) {
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.reminderRepository = reminderRepository;
    }

    /**
     * Googleカレンダーにイベントを作成し、リマインダーにevent IDを保存する
     * 
     * @param reminder イベントを作成するリマインダー情報
     * @param appUser  連携対象のAppUser
     */
    @Transactional
    public void createGoogleCalendarEvent(Reminder reminder, AppUser appUser) {
        googleAuthTokenRepository.findByAppUser(appUser)
                .ifPresent(authToken -> {
                    try {
                        GoogleCredential credential = new GoogleCredential.Builder()
                                .setClientSecrets(googleClientId, googleClientSecret)
                                .setJsonFactory(JacksonFactory.getDefaultInstance())
                                .setTransport(new NetHttpTransport())
                                .build();

                        if (authToken.getRefreshToken() != null) {
                            credential.setRefreshToken(authToken.getRefreshToken());
                            credential.refreshToken();
                            authToken.setAccessToken(credential.getAccessToken());
                            authToken.setExpiryDate(LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(credential.getExpirationTimeMilliseconds()), ZoneOffset.UTC));
                            authToken.setUpdatedAt(LocalDateTime.now());
                            googleAuthTokenRepository.save(authToken);
                        } else if (authToken.getAccessToken() != null && authToken.getExpiryDate() != null
                                && authToken.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(5))) {
                            credential.setAccessToken(authToken.getAccessToken());
                            credential.setExpirationTimeMilliseconds(
                                    authToken.getExpiryDate().toInstant(ZoneOffset.UTC).toEpochMilli());
                        } else {
                            System.err.println("No valid Google tokens found for user " + appUser.getUsername()
                                    + ". Cannot create Google Calendar event.");
                            return;
                        }

                        Calendar service = new Calendar.Builder(new NetHttpTransport(),
                                JacksonFactory.getDefaultInstance(), credential)
                                .setApplicationName("YourReminderApp")
                                .build();

                        Event event = new Event()
                                .setSummary(reminder.getCustomTitle())
                                .setDescription(reminder.getDescription());

                        LocalDateTime startDateTime = LocalDateTime.of(reminder.getRemindDate(),
                                reminder.getRemindTime());
                        EventDateTime start = new EventDateTime().setDateTime(new DateTime(startDateTime.toString()));
                        event.setStart(start);
                        EventDateTime end = new EventDateTime()
                                .setDateTime(new DateTime(startDateTime.plusHours(1).toString()));
                        event.setEnd(end);

                        String calendarId = "primary";
                        Event createdEvent = service.events().insert(calendarId, event).execute();

                        // Google Event IDをリマインダーに保存
                        reminder.setGoogleEventId(createdEvent.getId());
                        reminderRepository.save(reminder);

                    } catch (Exception e) {
                        System.err.println("Failed to create Google Calendar event for user " + appUser.getUsername()
                                + ": " + e.getMessage());
                    }
                });
    }

    // 必要に応じて、updateGoogleCalendarEvent, deleteGoogleCalendarEvent なども追加
}