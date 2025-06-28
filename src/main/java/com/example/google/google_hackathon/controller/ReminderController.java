package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.dto.ReminderResponse;
import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.service.ReminderService;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.service.GoogleCalendarService;
import com.example.google.google_hackathon.service.GoogleAuthException;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReminderResponse>> getAllRemindersForCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<Reminder> reminders = reminderService.getRemindersByUsername(username);
        List<ReminderResponse> responses = reminders.stream()
                .map(this::convertToDto) // convertToDto のロジックが修正される
                .collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> getReminderByIdForCurrentUser(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return reminderService.getReminderByIdAndUsername(id, username)
                .map(this::convertToDto) // convertToDto のロジックが修正される
                .map(reminder -> new ResponseEntity<>(reminder, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createReminder(@Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser currentUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB: " + username));

        // ★修正点1: convertToEntity メソッドを呼び出し、ReminderRequest の status を Reminder の
        // isCompleted に変換
        Reminder reminder = convertToEntity(reminderRequest);
        reminder.setAppUser(currentUser);

        try {
            Reminder createdReminder = reminderService.createReminder(reminder);

            if (linkToGoogleCalendar) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                // LocalTime に秒が含まれる場合は、パターンを "HH:mm:ss" に合わせる必要があります
                // もし reminderRequest.getRemindTime() が秒を含まない "HH:mm" 形式なら、このままでOK
                // そうでない場合は、reminderRequest.getRemindTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                // のように変換が必要です。ここでは、仮に "HH:mm" 形式と仮定します。
                LocalDateTime startDateTime = LocalDateTime
                        .parse(reminderRequest.getRemindDate() + "T" + reminderRequest.getRemindTime(), formatter);
                LocalDateTime endDateTime = startDateTime.plusHours(1);

                try {
                    JsonNode calendarEventResponse = googleCalendarService.createGoogleCalendarEvent(
                            SecurityContextHolder.getContext().getAuthentication(),
                            reminderRequest.getCustomTitle(),
                            reminderRequest.getDescription(),
                            startDateTime,
                            endDateTime,
                            "Asia/Tokyo");
                    logger.info("Google Calendar Event created for reminder ID {}: {}", createdReminder.getId(),
                            calendarEventResponse.toPrettyString());
                } catch (GoogleAuthException e) {
                    logger.warn("Google Calendar連携のために再認証が必要: {}", e.getMessage());
                    return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                            .body(Map.of("redirectUrl", e.getAuthUrl()));
                } catch (DateTimeParseException e) {
                    logger.error("リマインダーの日付/時刻のパースエラー: {}", e.getMessage(), e);
                } catch (Exception e) {
                    logger.error("Google Calendar連携中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
                }
            }
            return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);

        } catch (Exception e) {
            logger.error("リマインダー作成中にエラーが発生しました: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> updateReminder(@PathVariable Long id,
            @Valid @RequestBody ReminderRequest reminderRequest) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // ReminderService.updateReminder メソッドも ReminderRequest の変更に合わせて修正が必要です
        // ここでは DTO から Entity への変換をコントローラで行う前提で修正します。
        Reminder updatedReminder = reminderService.updateReminder(id, reminderRequest, username);

        if (updatedReminder != null) {
            return new ResponseEntity<>(convertToDto(updatedReminder), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        boolean deleted = reminderService.deleteReminder(id, username);

        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * ReminderエンティティをReminderResponse DTOに変換するヘルパーメソッド。
     * Reminderの isCompleted (Boolean) を ReminderResponse の status (String) に変換します。
     *
     * @param reminder 変換するReminderエンティティ
     * @return 変換されたReminderResponse DTO
     */
    private ReminderResponse convertToDto(Reminder reminder) {
        ReminderResponse dto = new ReminderResponse();
        dto.setId(reminder.getId());
        dto.setCustomTitle(reminder.getCustomTitle());
        dto.setRemindDate(reminder.getRemindDate());
        dto.setRemindTime(reminder.getRemindTime());
        dto.setDescription(reminder.getDescription());

        // ★修正点2: Reminder エンティティの isCompleted (Boolean) から ReminderResponse の status
        // (String) へ変換
        if (reminder.getIsCompleted() != null && reminder.getIsCompleted()) {
            dto.setStatus("COMPLETED"); // true なら "COMPLETED"
        } else {
            dto.setStatus("PENDING"); // false または null なら "PENDING"
        }

        if (reminder.getAppUser() != null) {
            dto.setUsername(reminder.getAppUser().getUsername());
        }
        return dto;
    }

    /**
     * ReminderRequest DTOをReminderエンティティに変換するヘルパーメソッド。
     * ReminderRequest の status (String) を Reminderエンティティの isCompleted (Boolean)
     * に変換します。
     *
     * @param dto 変換するReminderRequest DTO
     * @return 変換されたReminderエンティティ
     */
    private Reminder convertToEntity(ReminderRequest dto) {
        Reminder entity = new Reminder();
        entity.setCustomTitle(dto.getCustomTitle());
        entity.setRemindDate(dto.getRemindDate());
        entity.setRemindTime(dto.getRemindTime());
        entity.setDescription(dto.getDescription());

        // ★修正点3: ReminderRequest の status (String) から Reminder エンティティの isCompleted
        // (Boolean) へ変換
        // 例: "COMPLETED" なら true、それ以外なら false（"PENDING"なども含む）
        if (dto.getStatus() != null && "COMPLETED".equalsIgnoreCase(dto.getStatus())) {
            entity.setIsCompleted(true);
        } else {
            entity.setIsCompleted(false); // "PENDING" やその他の文字列、または null の場合は false
        }
        return entity;
    }
}