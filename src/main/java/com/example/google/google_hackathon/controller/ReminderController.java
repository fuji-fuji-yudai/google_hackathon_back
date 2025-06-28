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
import java.time.LocalDate; // 追加: LocalDate を使用するため
import java.time.LocalTime; // 追加: LocalTime を使用するため
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
    public ResponseEntity<?> createReminder(@Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar) { // linkToGoogleCalendar パラメータを復活

        logger.info("========== ReminderController.createReminder メソッド開始。リクエストボディ: {}, Google連携フラグ: {}",
                reminderRequest, linkToGoogleCalendar); // リクエストボディとフラグをログ出力

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

        // 日付と時刻のパース
        // Google Calendar連携で使用するため、StringからLocalDateTimeへ変換
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            startDateTime = LocalDateTime.parse(reminderRequest.getRemindDate() + "T" + reminderRequest.getRemindTime(),
                    formatter);
            endDateTime = startDateTime.plusHours(1); // 仮に1時間のイベントとして設定
        } catch (DateTimeParseException e) {
            logger.error("リマインダーの日付/時刻のパースエラー: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "日付または時刻の形式が不正です。"));
        }

        // まずリマインダーをDBに保存 (Google Calendar連携の成否に関わらずリマインダーは保存)
        Reminder reminder = convertToEntity(reminderRequest); // ここでDTOからEntityへの変換が行われる
        reminder.setAppUser(currentUser);
        Reminder createdReminder = reminderService.createReminder(reminder);
        logger.info("リマインダーをDBに保存しました。ID: {}", createdReminder.getId());

        // Google Calendar連携のロジックを条件分岐で実行
        if (linkToGoogleCalendar) {
            try {
                logger.info("Google Calendar Event の作成を試行します。タイトル: {}, 開始: {}, 終了: {}",
                        reminderRequest.getCustomTitle(), startDateTime, endDateTime);

                JsonNode calendarEventResponse = googleCalendarService.createGoogleCalendarEvent(
                        SecurityContextHolder.getContext().getAuthentication(),
                        reminderRequest.getCustomTitle(),
                        reminderRequest.getDescription(),
                        startDateTime,
                        endDateTime,
                        "Asia/Tokyo");

                logger.info("Google Calendar Event の作成結果 (JsonNode): {}",
                        calendarEventResponse != null ? calendarEventResponse.toPrettyString() : "null");

                // Google Calendar Event IDをリマインダーに保存するロジック
                if (calendarEventResponse != null && calendarEventResponse.has("id")) {
                    String googleEventId = calendarEventResponse.get("id").asText();
                    createdReminder.setGoogleEventId(googleEventId);
                    // ここで createdReminder の googleEventId が DB に永続化されるように、
                    // ReminderService の update メソッドを呼び出す必要があります。
                    // 例: createdReminder = reminderService.updateReminder(createdReminder.getId(),
                    // reminderRequest, username);
                    // または、ReminderServiceに Google Event IDのみを更新する専用のメソッドを追加し呼び出す。
                    // 現状のcreateReminder(reminder)は初回保存なので、イベントIDは別途更新が必要です。
                    logger.info("Google Event ID '{}' をリマインダーID '{}' に関連付けました。", googleEventId,
                            createdReminder.getId());

                    // ★重要: DBにGoogle Event IDを保存するには、ここでcreatedReminderを再度保存する必要がある
                    // ReminderServiceに以下のようなメソッドを追加し、呼び出すことを推奨
                    // reminderService.updateGoogleEventId(createdReminder.getId(), googleEventId);
                    // または、createdReminder = reminderService.save(createdReminder); //
                    // saveメソッドが更新も兼ねる場合
                } else {
                    logger.warn("Google Calendar Event は作成されましたが、イベントIDがレスポンスに含まれていませんでした。");
                }

                logger.info("リマインダー作成とGoogleカレンダー連携が完了しました。リマインダーID: {}", createdReminder.getId());
                // Google連携が成功した場合も、createdReminderの最新情報（GoogleEventIdを含む）をDTOに変換して返す
                return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);

            } catch (GoogleAuthException e) {
                // Google認証が必要な場合は、リダイレクトURLを返す
                logger.warn("Google Calendar連携のために再認証が必要: {}", e.getMessage(), e); // 例外スタックトレースも含む
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .body(Map.of("redirectUrl", e.getAuthUrl()));
            } catch (Exception e) { // その他の予期せぬエラー
                logger.error("Google Calendar連携中に予期せぬエラーが発生しました: {}", e.getMessage(), e); // 例外スタックトレースも含む
                // Google連携が失敗してもリマインダー作成自体は成功しているため、
                // ここではINTERNAL_SERVER_ERRORを返すが、フロントエンドのUXによっては201で成功を返し、
                // Google連携が失敗したことを別の方法で通知することも検討可能
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            // Google連携が不要な場合
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

        // Reminderエンティティ (LocalDate/LocalTime) -> ReminderResponse DTO (String) へ変換
        if (reminder.getRemindDate() != null) {
            dto.setRemindDate(reminder.getRemindDate().format(DateTimeFormatter.ISO_LOCAL_DATE)); // "YYYY-MM-DD"
        }
        if (reminder.getRemindTime() != null) {
            dto.setRemindTime(reminder.getRemindTime().format(DateTimeFormatter.ofPattern("HH:mm"))); // "HH:mm"
            // 必要に応じて、秒まで含めるなら以下を使用
            // dto.setRemindTime(reminder.getRemindTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
        }

        dto.setDescription(reminder.getDescription());
        dto.setGoogleEventId(reminder.getGoogleEventId()); // Google Event IDもDTOに含める

        // Reminder エンティティの isCompleted (Boolean) から ReminderResponse の status (String)
        // へ変換
        if (reminder.getIsCompleted() != null && reminder.getIsCompleted()) {
            dto.setStatus("COMPLETED");
        } else {
            dto.setStatus("PENDING");
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
        entity.setDescription(dto.getDescription());

        // ReminderRequest DTO (String) -> Reminderエンティティ (LocalDate/LocalTime) へ変換
        if (dto.getRemindDate() != null && !dto.getRemindDate().isEmpty()) {
            entity.setRemindDate(LocalDate.parse(dto.getRemindDate()));
        }
        if (dto.getRemindTime() != null && !dto.getRemindTime().isEmpty()) {
            try {
                entity.setRemindTime(LocalTime.parse(dto.getRemindTime(), DateTimeFormatter.ofPattern("HH:mm")));
            } catch (DateTimeParseException e) {
                // "HH:mm:ss" 形式も試す、またはエラーハンドリング
                entity.setRemindTime(LocalTime.parse(dto.getRemindTime(), DateTimeFormatter.ISO_LOCAL_TIME));
            }
        }
        // entity.setGoogleEventId(dto.getGoogleEventId()); // リクエストからは通常セットしないが、必要なら追加

        // ReminderRequest の status (String) から Reminder エンティティの isCompleted (Boolean)
        // へ変換
        if (dto.getStatus() != null && "COMPLETED".equalsIgnoreCase(dto.getStatus())) {
            entity.setIsCompleted(true);
        } else {
            entity.setIsCompleted(false);
        }
        return entity;
    }
}