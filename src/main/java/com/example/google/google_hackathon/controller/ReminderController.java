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

import org.springframework.beans.factory.annotation.Value;

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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// ★重要: com.google.api.client.util.Value をインポートしている場合は削除してください。
// もし使っているなら、その箇所で完全修飾名で記述するか、別名インポート (import com.google.api.client.util.Value as GoogleValue;) を検討してください。

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private static final Logger logger = LoggerFactory.getLogger(ReminderController.class);

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository;
    private final GoogleCalendarService googleCalendarService;

    // フロントエンドのリダイレクトベースURLを注入
    @Value("${app.frontend.redirect-url}") // application.properties のキーと一致させる
    private String frontendRedirectBaseUrl;

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
                .map(this::convertToDto)
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
                .map(this::convertToDto)
                .map(reminder -> new ResponseEntity<>(reminder, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createReminder(
            @Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar,
            @RequestHeader(value = "X-Google-Access-Token", required = false) String googleAccessToken) {

        logger.info("========== ReminderController.createReminder メソッド開始。リクエストボディ: {}, Google連携フラグ: {}",
                reminderRequest, linkToGoogleCalendar);
        logger.info("Googleアクセストークン提供済み: {}", googleAccessToken != null ? "はい" : "いいえ");

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
        LocalDateTime endDateTime;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            // ReminderRequestのgetRemindDate()とgetRemindTime()が存在すると仮定
            startDateTime = LocalDateTime.parse(reminderRequest.getRemindDate() + "T" + reminderRequest.getRemindTime(),
                    formatter);
            endDateTime = startDateTime.plusHours(1); // 仮に1時間のイベントとして設定
        } catch (DateTimeParseException e) {
            logger.error("リマインダーの日付/時刻のパースエラー: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "日付または時刻の形式が不正です。"));
        }

        // Reminderエンティティのコンストラクタエラーを避けるため、
        // Reminderエンティティに@NoArgsConstructorが付与されていることを前提に
        // デフォルトコンストラクタでインスタンス化
        // もしReminderエンティティに引数付きコンストラクタしかなく、@NoArgsConstructorがない場合、
        // Reminderエンティティを修正するか、適切な引数を与えてコンストラクタを呼び出す必要があります。
        Reminder reminder = convertToEntity(reminderRequest); // このメソッド内で Reminder のインスタンス化が行われる
        reminder.setAppUser(currentUser);
        Reminder createdReminder = reminderService.createReminder(reminder);
        logger.info("リマインダーをDBに保存しました。ID: {}", createdReminder.getId());

        if (linkToGoogleCalendar) {
            if (googleAccessToken == null || googleAccessToken.isEmpty()) {
                logger.warn("Google連携が要求されましたが、X-Google-Access-Token ヘッダーがありません。");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Googleカレンダー連携にはアクセストークンが必要です。"));
            }

            try {
                logger.info("Google Calendar Event の作成を試行します。タイトル: {}, 開始: {}, 終了: {}",
                        reminderRequest.getCustomTitle(), startDateTime, endDateTime);

                JsonNode calendarEventResponse = googleCalendarService.createGoogleCalendarEvent(
                        googleAccessToken, // 正しい引数を渡す
                        reminderRequest.getCustomTitle(),
                        reminderRequest.getDescription(),
                        startDateTime,
                        endDateTime,
                        "Asia/Tokyo");

                logger.info("Google Calendar Event の作成結果 (JsonNode): {}",
                        calendarEventResponse != null ? calendarEventResponse.toPrettyString() : "null");

                if (calendarEventResponse != null && calendarEventResponse.has("id")) {
                    String googleEventId = calendarEventResponse.get("id").asText();
                    createdReminder.setGoogleEventId(googleEventId);
                    // ReminderService に Google Event ID のみを更新するメソッドがあればそれが理想。
                    // なければ、reminderService.updateReminder(createdReminder.getId(), reminderRequest,
                    // username);
                    // のように適切な update メソッドを呼び出す。
                    // ここでは仮に、ReminderService の createReminder が更新も兼ねるか、
                    // または reminderService.save(createdReminder); のように変更がDBに反映されると仮定
                    reminderService.createReminder(createdReminder); // 例: 再保存で対応（要ReminderService確認）
                    logger.info("Google Event ID '{}' をリマインダーID '{}' に関連付けました。", googleEventId,
                            createdReminder.getId());
                } else {
                    logger.warn("Google Calendar Event は作成されましたが、イベントIDがレスポンスに含まれていませんでした。");
                }

                logger.info("リマインダー作成とGoogleカレンダー連携が完了しました。リマインダーID: {}", createdReminder.getId());
                return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);

            } catch (GoogleAuthException e) {
                logger.warn("Google Calendar連携のために再認証が必要: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .body(Map.of("redirectUrl", e.getAuthUrl() != null ? e.getAuthUrl()
                                : frontendRedirectBaseUrl + "/google-calendar-auth"));
            } catch (Exception e) {
                logger.error("Google Calendar連携中に予期せぬエラーが発生しました: {}", e.getMessage(), e);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            logger.info("Googleカレンダー連携はリクエストされませんでした。リマインダーID: {}", createdReminder.getId());
            return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);
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

    private ReminderResponse convertToDto(Reminder reminder) {
        ReminderResponse dto = new ReminderResponse();
        dto.setId(reminder.getId()); // getId() の呼び出し
        dto.setCustomTitle(reminder.getCustomTitle()); // getCustomTitle() の呼び出し

        if (reminder.getRemindDate() != null) {
            dto.setRemindDate(reminder.getRemindDate().format(DateTimeFormatter.ISO_LOCAL_DATE)); // getRemindDate()
                                                                                                  // の呼び出し
        }
        if (reminder.getRemindTime() != null) {
            dto.setRemindTime(reminder.getRemindTime().format(DateTimeFormatter.ofPattern("HH:mm"))); // getRemindTime()
                                                                                                      // の呼び出し
        }

        dto.setDescription(reminder.getDescription()); // getDescription() の呼び出し
        dto.setGoogleEventId(reminder.getGoogleEventId()); // getGoogleEventId() の呼び出し

        if (reminder.getIsCompleted() != null && reminder.getIsCompleted()) { // getIsCompleted() の呼び出し
            dto.setStatus("COMPLETED"); // setStatus() の呼び出し
        } else {
            dto.setStatus("PENDING"); // setStatus() の呼び出し
        }

        if (reminder.getAppUser() != null) { // getAppUser() の呼び出し
            dto.setUsername(reminder.getAppUser().getUsername()); // getAppUser().getUsername() の呼び出し
        }
        return dto;
    }

    private Reminder convertToEntity(ReminderRequest dto) {
        // Reminderエンティティに@NoArgsConstructorが付与されていることを前提に
        // デフォルトコンストラクタでインスタンス化
        // もし引数付きコンストラクタしかない場合は、Reminderエンティティに@NoArgsConstructorを追加するか、
        // new Reminder(dto.getCustomTitle(), LocalDate.parse(dto.getRemindDate()), ...)
        // のように適切な引数で呼び出す必要があります。
        Reminder entity = new Reminder(); // ★Reminderコンストラクタの呼び出し

        entity.setCustomTitle(dto.getCustomTitle()); // setCustomTitle() の呼び出し
        entity.setDescription(dto.getDescription()); // setDescription() の呼び出し

        if (dto.getRemindDate() != null && !dto.getRemindDate().isEmpty()) { // getRemindDate() の呼び出し
            entity.setRemindDate(LocalDate.parse(dto.getRemindDate())); // setRemindDate() の呼び出し
        }
        if (dto.getRemindTime() != null && !dto.getRemindTime().isEmpty()) { // getRemindTime() の呼び出し
            try {
                entity.setRemindTime(LocalTime.parse(dto.getRemindTime(), DateTimeFormatter.ofPattern("HH:mm"))); // setRemindTime()
                                                                                                                  // の呼び出し
            } catch (DateTimeParseException e) {
                entity.setRemindTime(LocalTime.parse(dto.getRemindTime(), DateTimeFormatter.ISO_LOCAL_TIME)); // setRemindTime()
                                                                                                              // の呼び出し
            }
        }

        // entity.setGoogleEventId(dto.getGoogleEventId()); // リクエストからは通常セットしない

        if (dto.getStatus() != null && "COMPLETED".equalsIgnoreCase(dto.getStatus())) { // getStatus() の呼び出し
            entity.setIsCompleted(true); // setIsCompleted() の呼び出し
        } else {
            entity.setIsCompleted(false); // setIsCompleted() の呼び出し
        }
        return entity;
    }
}
