package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.service.GoogleCalendarService;
import com.example.google.google_hackathon.service.ReminderService;
import com.example.google.google_hackathon.dto.ReminderDto;
import com.example.google.google_hackathon.dto.ReminderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private static final Logger logger = LoggerFactory.getLogger(ReminderController.class);

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository;
    private final GoogleCalendarService googleCalendarService;

    public ReminderController(ReminderService reminderService, AppUserRepository appUserRepository,
            GoogleCalendarService googleCalendarService) {
        this.reminderService = reminderService;
        this.appUserRepository = appUserRepository;
        this.googleCalendarService = googleCalendarService;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    private Reminder convertToEntity(ReminderRequest reminderRequest) {
        Reminder reminder = new Reminder();
        reminder.setCustomTitle(reminderRequest.getCustomTitle());
        reminder.setDescription(reminderRequest.getDescription());

        try {
            reminder.setRemindDate(LocalDate.parse(reminderRequest.getRemindDate()));
            reminder.setRemindTime(LocalTime.parse(reminderRequest.getRemindTime()));
            if (reminderRequest.getNextRemindTime() != null && !reminderRequest.getNextRemindTime().isEmpty()) {
                reminder.setNextRemindTime(LocalDateTime.parse(reminderRequest.getNextRemindTime(),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                reminder.setNextRemindTime(null); // 明示的にnullを設定
            }
        } catch (DateTimeParseException e) {
            logger.error("日付または時刻のパースエラー: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid date or time format in ReminderRequest", e);
        }

        reminder.setRecurrenceType(reminderRequest.getRecurrenceType());
        return reminder;
    }

    private ReminderDto convertToDto(Reminder reminder) {
        return new ReminderDto(
                reminder.getId(),
                reminder.getCustomTitle(),
                reminder.getDescription(),
                reminder.getRemindDate() != null ? reminder.getRemindDate().toString() : null,
                reminder.getRemindTime() != null ? reminder.getRemindTime().toString() : null,
                reminder.getRecurrenceType(),
                reminder.getNextRemindTime() != null
                        ? reminder.getNextRemindTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null,
                reminder.getGoogleEventId());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createReminder(
            @Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar) {

        logger.info("========== ReminderController.createReminder メソッド開始。リクエストボディ: {}, Google連携フラグ: {}",
                reminderRequest, linkToGoogleCalendar);

        String username = getCurrentUsername();
        if (username == null) {
            logger.warn("未認証ユーザーによるリマインダー作成リクエストが拒否されました。");
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser currentUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("認証されたユーザー '{}' がDBで見つかりません。", username);
                    return new RuntimeException("Authenticated user not found in DB: " + username);
                });

        LocalDateTime startDateTime;
        try {
            startDateTime = LocalDateTime.parse(
                    reminderRequest.getRemindDate() + "T" + reminderRequest.getRemindTime(),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME // 例: "2023-10-27T10:30"
            );
        } catch (DateTimeParseException e) {
            logger.error("リマインダーの日付/時刻のパースエラー: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "日付または時刻の形式が不正です。"));
        }
        LocalDateTime endDateTime = startDateTime.plusHours(1);

        Reminder reminder = convertToEntity(reminderRequest);
        reminder.setAppUser(currentUser);
        Reminder createdReminder = reminderService.createReminder(reminder);
        logger.info("リマインダーをDBに保存しました。ID: {}", createdReminder.getId());

        if (linkToGoogleCalendar) {
            try {
                logger.info("Google Calendar Event の作成を試行します。タイトル: {}, 開始: {}, 終了: {}, 参加者: {}",
                        reminderRequest.getCustomTitle(), startDateTime, endDateTime,
                        reminderRequest.getAttendeeEmails());

                JsonNode calendarEventResponse = googleCalendarService.createGoogleCalendarEvent(
                        reminderRequest.getCustomTitle(),
                        reminderRequest.getDescription(),
                        startDateTime,
                        endDateTime,
                        "Asia/Tokyo",
                        reminderRequest.getAttendeeEmails());

                logger.info("Google Calendar Event の作成結果 (JsonNode): {}",
                        calendarEventResponse != null ? calendarEventResponse.toPrettyString() : "null");

                if (calendarEventResponse != null && calendarEventResponse.has("id")) {
                    String googleEventId = calendarEventResponse.get("id").asText();
                    createdReminder.setGoogleEventId(googleEventId);
                    reminderService.updateReminder(createdReminder);
                    logger.info("Google Event ID '{}' をリマインダーID '{}' に関連付けました。", googleEventId,
                            createdReminder.getId());
                } else {
                    logger.warn("Google Calendar Event は作成されましたが、イベントIDがレスポンスに含まれていませんでした。");
                    return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);
                }

                logger.info("リマインダー作成とGoogleカレンダー連携が完了しました。リマインダーID: {}", createdReminder.getId());
                return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);

            } catch (IOException | GeneralSecurityException e) {
                logger.error("Google Calendar連携中にエラーが発生しました: {}", e.getMessage(), e);
                return new ResponseEntity<>(Map.of("message", "Googleカレンダー連携エラー: " + e.getMessage()),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception e) {
                logger.error("Google Calendar連携中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            logger.info("Googleカレンダー連携はリクエストされませんでした。リマインダーID: {}", createdReminder.getId());
            return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderDto> getReminderById(@PathVariable Long id) {
        Reminder reminder = reminderService.getReminderById(id)
                .orElseThrow(() -> new RuntimeException("Reminder not found with id: " + id));
        if (!reminder.getAppUser().getUsername().equals(getCurrentUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to reminder");
        }
        return ResponseEntity.ok(convertToDto(reminder));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReminderDto>> getAllReminders() {
        AppUser currentUser = appUserRepository.findByUsername(getCurrentUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        List<Reminder> reminders = reminderService.getRemindersByUser(currentUser);
        List<ReminderDto> reminderDtos = reminders.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(reminderDtos);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateReminder(@PathVariable Long id,
            @Valid @RequestBody ReminderRequest reminderRequest) { // ★型を<?>に修正★
        Reminder existingReminder = reminderService.getReminderById(id)
                .orElseThrow(() -> new RuntimeException("Reminder not found with id: " + id));
        if (!existingReminder.getAppUser().getUsername().equals(getCurrentUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to reminder");
        }

        existingReminder.setCustomTitle(reminderRequest.getCustomTitle());
        existingReminder.setDescription(reminderRequest.getDescription());
        try {
            existingReminder.setRemindDate(LocalDate.parse(reminderRequest.getRemindDate()));
            existingReminder.setRemindTime(LocalTime.parse(reminderRequest.getRemindTime()));
            if (reminderRequest.getNextRemindTime() != null && !reminderRequest.getNextRemindTime().isEmpty()) {
                existingReminder.setNextRemindTime(LocalDateTime.parse(reminderRequest.getNextRemindTime(),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                existingReminder.setNextRemindTime(null);
            }
        } catch (DateTimeParseException e) {
            logger.error("リマインダー更新時の日付/時刻のパースエラー: {}", e.getMessage(), e);
            // ★この行を修正★ ResponseEntity.ok()ではなく、HttpStatus.BAD_REQUESTでMapを返す
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "更新データの日付または時刻の形式が不正です。"));
        }
        existingReminder.setRecurrenceType(reminderRequest.getRecurrenceType());

        Reminder updatedReminder = reminderService.updateReminder(existingReminder);
        return ResponseEntity.ok(convertToDto(updatedReminder));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        Reminder reminderToDelete = reminderService.getReminderById(id)
                .orElseThrow(() -> new RuntimeException("Reminder not found with id: " + id));
        if (!reminderToDelete.getAppUser().getUsername().equals(getCurrentUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to reminder");
        }
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }
}