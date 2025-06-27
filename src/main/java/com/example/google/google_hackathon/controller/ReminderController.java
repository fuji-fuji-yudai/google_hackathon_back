package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.dto.ReminderResponse;
import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.service.ReminderService;
import com.example.google.google_hackathon.repository.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository;

    public ReminderController(ReminderService reminderService, AppUserRepository appUserRepository) {
        this.reminderService = reminderService;
        this.appUserRepository = appUserRepository;
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
        // @PreAuthorize があればこのチェックは冗長ですが、念のため残すことも可能です。
        // もし認証に失敗していれば、そもそもここには到達しません。
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
     * @param reminderRequest 作成するリマインダーのデータを含むDTO
     * @return 作成されたリマインダーデータとHTTPステータスCREATED
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> createReminder(@Valid @RequestBody ReminderRequest reminderRequest) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // AppUserエンティティを取得（リマインダーに紐づけるため）
        AppUser currentUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB: " + username));

        Reminder reminder = convertToEntity(reminderRequest);
        reminder.setAppUser(currentUser); // 作成するリマインダーに現在のユーザーを紐づける

        // createReminder が Google Calendar と同期するロジックを含む想定
        Reminder createdReminder = reminderService.createReminder(reminder);
        return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);
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

        // 更新対象のリマインダーが現在のユーザーに属するか確認するため、IDとユーザー名を渡す
        // supdateReminder が Google Calendar と同期するロジックを含む想定
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

        // 削除対象のリマインダーが現在のユーザーに属するか確認するため、IDとユーザー名を渡す
        // deleteReminder が Google Calendar と同期するロジックを含む想定
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
        // ★修正: ReminderエンティティのisCompletedをDTOのisCompletedに設定
        dto.setIsCompleted(reminder.getIsCompleted());
        // username はAppUserから取得して設定
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
        // IDは通常、DBが自動生成するため、DTOから設定しない
        entity.setCustomTitle(dto.getCustomTitle());
        entity.setRemindDate(dto.getRemindDate());
        entity.setRemindTime(dto.getRemindTime());
        entity.setDescription(dto.getDescription());

        // isCompletedはBoolean型なので、Boolean値を設定する
        // ReminderRequest DTOのisCompletedがnullの場合、false（未完了）をデフォルトとするのが一般的
        entity.setIsCompleted(dto.getIsCompleted() != null ? dto.getIsCompleted() : false);

        // appUserはここで設定しない（通常はControllerやServiceで設定される）
        return entity;
    }
}