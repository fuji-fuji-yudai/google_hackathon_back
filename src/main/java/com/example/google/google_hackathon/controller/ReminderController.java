package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.ReminderRequest; // DTOをインポート
import com.example.google.google_hackathon.dto.ReminderResponse; // DTOをインポート
import com.example.google.google_hackathon.entity.AppUser; // AppUserをインポート
import com.example.google.google_hackathon.entity.Reminder; // Reminderエンティティをインポート
import com.example.google.google_hackathon.service.ReminderService; // ReminderServiceをインポート
import com.example.google.google_hackathon.repository.AppUserRepository; // AppUserRepositoryをインポート

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // 認可のために追加
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid; // バリデーションのために追加
import java.util.List;
import java.util.stream.Collectors;

@RestController // RESTful APIのコントローラーであることを示す
@RequestMapping("/api/reminders") // このコントローラーのベースパス
public class ReminderController {

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository; // 現在のユーザーを取得するために必要

    public ReminderController(ReminderService reminderService, AppUserRepository appUserRepository) {
        this.reminderService = reminderService;
        this.appUserRepository = appUserRepository;
    }

    // 現在認証されているユーザーのユーザー名を取得するヘルパーメソッド
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null; // 認証されていない場合
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return principal.toString(); // それ以外の場合（例:匿名ユーザー）
    }

    // ユーザーに紐づく全てのリマインダーを取得
    @GetMapping
    @PreAuthorize("isAuthenticated()") // 認証されたユーザーのみアクセス可能
    public ResponseEntity<List<ReminderResponse>> getAllRemindersForCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED); // 認証されていない場合
        }
        List<Reminder> reminders = reminderService.getRemindersByUsername(username); // Service層にユーザー名を渡す
        List<ReminderResponse> responses = reminders.stream()
                .map(this::convertToDto) // EntityをDTOに変換
                .collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    // ユーザーに紐づく、特定のIDのリマインダーを取得
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> getReminderByIdForCurrentUser(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return reminderService.getReminderByIdAndUsername(id, username) // Service層にIDとユーザー名を渡す
                .map(this::convertToDto) // EntityをDTOに変換
                .map(reminder -> new ResponseEntity<>(reminder, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // 見つからないか、他人のリマインダーの場合
    }

    // 新しいリマインダーを作成
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

        Reminder createdReminder = reminderService.createReminder(reminder);
        return new ResponseEntity<>(convertToDto(createdReminder), HttpStatus.CREATED);
    }

    // 既存のリマインダーを更新
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> updateReminder(@PathVariable Long id,
            @Valid @RequestBody ReminderRequest reminderRequest) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // 更新対象のリマインダーが現在のユーザーに属するか確認するため、IDとユーザー名を渡す
        Reminder updatedReminder = reminderService.updateReminder(id, reminderRequest, username);

        if (updatedReminder != null) {
            return new ResponseEntity<>(convertToDto(updatedReminder), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 見つからないか、他人のリマインダーの場合
    }

    // リマインダーを削除
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        String username = getCurrentUsername();
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // 削除対象のリマインダーが現在のユーザーに属するか確認するため、IDとユーザー名を渡す
        boolean deleted = reminderService.deleteReminder(id, username);

        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 削除成功
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 見つからないか、他人のリマインダーの場合
    }

    // EntityからDTOへの変換ヘルパー
    private ReminderResponse convertToDto(Reminder reminder) {
        ReminderResponse dto = new ReminderResponse();
        dto.setId(reminder.getId());
        dto.setCustomTitle(reminder.getCustomTitle());
        dto.setRemindDate(reminder.getRemindDate());
        dto.setRemindTime(reminder.getRemindTime());
        dto.setDescription(reminder.getDescription());
        dto.setStatus(reminder.getStatus());
        // dto.setUsername(reminder.getAppUser().getUsername()); // 必要ならユーザー名もDTOに含める
        return dto;
    }

    // DTOからEntityへの変換ヘルパー
    private Reminder convertToEntity(ReminderRequest dto) {
        Reminder entity = new Reminder();
        // IDはDBが自動生成するため設定しない
        entity.setCustomTitle(dto.getCustomTitle());
        entity.setRemindDate(dto.getRemindDate());
        entity.setRemindTime(dto.getRemindTime());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING"); // DTOでステータスが指定されなければPENDING
        // appUserはここで設定しない（createReminderメソッド内で設定する）
        return entity;
    }
}