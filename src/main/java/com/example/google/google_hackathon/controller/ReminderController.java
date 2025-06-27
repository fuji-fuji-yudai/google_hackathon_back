package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.dto.ReminderResponse;
import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.service.ReminderService;
import com.example.google.google_hackathon.repository.AppUserRepository;
// ★追加: Googleカレンダーサービスをインポートします
import com.example.google.google_hackathon.service.GoogleCalendarService;
// ★追加: GoogleAuthException をインポートします
import com.example.google.google_hackathon.service.GoogleAuthException;
// ★追加: Map をインポートします（リダイレクトURLを返すため）
import java.util.Map;
// ★追加: JsonNode をインポートします
import com.fasterxml.jackson.databind.JsonNode;
// ★追加: ロギング
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
import java.time.LocalDateTime; // LocalDateTimeのインポート
import java.time.format.DateTimeFormatter; // DateTimeFormatterのインポート
import java.time.format.DateTimeParseException; // DateTimeParseExceptionのインポート

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private static final Logger logger = LoggerFactory.getLogger(ReminderController.class); // ロガーの追加

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository;
    private final GoogleCalendarService googleCalendarService; // ★追加: GoogleCalendarService を追加

    // コンストラクタに GoogleCalendarService を追加
    public ReminderController(ReminderService reminderService, AppUserRepository appUserRepository,
            GoogleCalendarService googleCalendarService) {
        this.reminderService = reminderService;
        this.appUserRepository = appUserRepository;
        this.googleCalendarService = googleCalendarService; // ★追加: 初期化
    }

    /**
     * 現在認証されているユーザーのユーザー名を取得するヘルパーメソッド
     *
     * @return 認証されているユーザーのユーザー名、または認証されていない場合はnull
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null; // 認証されていない場合 (通常 @PreAuthorize で阻止される)
        }
        // Authentication.getName() は、通常、UserDetails.getUsername() の値が設定される
        return authentication.getName();
    }

    /**
     * 現在のユーザーに紐づく全てのリマインダーを取得します。
     *
     * @return リマインダーのリストとHTTPステータスOK
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()") // 認証されたユーザーのみアクセス可能であることを保証
    public ResponseEntity<List<ReminderResponse>> getAllRemindersForCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        List<Reminder> reminders = reminderService.getRemindersByUsername(username);
        List<ReminderResponse> responses = reminders.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    /**
     * 現在のユーザーに紐づく、特定のIDのリマインダーを取得します。
     *
     * @param id リマインダーのID
     * @return リマインダーデータとHTTPステータスOK、または見つからない場合はNOT_FOUND
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> getReminderByIdForCurrentUser(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return reminderService.getReminderByIdAndUsername(id, username)
                .map(this::convertToDto)
                .map(reminder -> new ResponseEntity<>(reminder, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // 見つからないか、他人のリマインダーの場合
    }

    /**
     * 新しいリマインダーを作成します。
     *
     * @param reminderRequest      作成するリマインダーのデータを含むDTO
     * @param linkToGoogleCalendar Googleカレンダーと連携するかどうか (★追加)
     * @return 作成されたリマインダーデータとHTTPステータスCREATED、またはリダイレクト情報
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createReminder(@Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar) { // ★修正点1: linkToGoogleCalendar
                                                                                  // パラメータを追加
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser currentUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB: " + username));

        Reminder reminder = convertToEntity(reminderRequest);
        reminder.setAppUser(currentUser);

        try {
            Reminder createdReminder = reminderService.createReminder(reminder);

            // ★修正点2: Googleカレンダー連携ロジックを追加
            if (linkToGoogleCalendar) {
                // 日付と時刻文字列をLocalDateTimeにパース
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                LocalDateTime startDateTime = LocalDateTime
                        .parse(reminderRequest.getRemindDate() + "T" + reminderRequest.getRemindTime(), formatter);
                LocalDateTime endDateTime = startDateTime.plusHours(1); // デフォルトで1時間後のイベントとして仮設定

                try {
                    // GoogleCalendarService を呼び出してイベント作成を試みる
                    JsonNode calendarEventResponse = googleCalendarService.createGoogleCalendarEvent(
                            SecurityContextHolder.getContext().getAuthentication(), // 現在の認証情報を渡す
                            reminderRequest.getCustomTitle(),
                            reminderRequest.getDescription(),
                            startDateTime,
                            endDateTime,
                            "Asia/Tokyo" // デフォルトのタイムゾーン。必要であればリクエストから取得
                    );
                    logger.info("Google Calendar Event created for reminder ID {}: {}", createdReminder.getId(),
                            calendarEventResponse.toPrettyString());
                } catch (GoogleAuthException e) {
                    logger.warn("Google Calendar連携のために再認証が必要: {}", e.getMessage());
                    // 権限不足などでGoogle認証が必要な場合、フロントエンドにリダイレクトURLを返す
                    return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                            .body(Map.of("redirectUrl", e.getAuthUrl()));
                } catch (DateTimeParseException e) {
                    logger.error("リマインダーの日付/時刻のパースエラー: {}", e.getMessage(), e);
                    // ここではリマインダー自体は作成されているので、カレンダー連携エラーとしてログに記録し、リマインダーは返す
                } catch (Exception e) {
                    logger.error("Google Calendar連携中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
                    // ここではリマインダー自体は作成されているので、カレンダー連携エラーとしてログに記録し、リマインダーは返す
                }
            }
            return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);

        } catch (Exception e) {
            logger.error("リマインダー作成中にエラーが発生しました: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 既存のリマインダーを更新します。
     *
     * @param id              更新するリマインダーのID
     * @param reminderRequest 更新データを含むDTO
     * @return 更新されたリマインダーデータとHTTPステータスOK、または見つからない場合はNOT_FOUND
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> updateReminder(@PathVariable Long id,
            @Valid @RequestBody ReminderRequest reminderRequest) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        Reminder updatedReminder = reminderService.updateReminder(id, reminderRequest, username);

        if (updatedReminder != null) {
            return new ResponseEntity<>(convertToDto(updatedReminder), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * リマインダーを削除します。
     *
     * @param id 削除するリマインダーのID
     * @return HTTPステータスNO_CONTENT（削除成功）、またはNOT_FOUND
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        boolean deleted = reminderService.deleteReminder(id, username);

        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 削除成功
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * ReminderエンティティをReminderResponse DTOに変換するヘルパーメソッド。
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
        dto.setIsCompleted(reminder.getIsCompleted());
        if (reminder.getAppUser() != null) {
            dto.setUsername(reminder.getAppUser().getUsername());
        }
        return dto;
    }

    /**
     * ReminderRequest DTOをReminderエンティティに変換するヘルパーメソッド。
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
        entity.setIsCompleted(dto.getIsCompleted() != null ? dto.getIsCompleted() : false);
        return entity;
    }
}