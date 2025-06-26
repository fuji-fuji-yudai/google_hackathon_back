package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.dto.ReminderResponse;
import com.example.google.google_hackathon.entity.AppUser; // AppUserをimport
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.service.ReminderService;
import com.example.google.google_hackathon.repository.AppUserRepository; // AppUserRepositoryをimport (findByUsername用)

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // 既存のimport
import org.springframework.security.core.context.SecurityContextHolder; // 既存のimport
import org.springframework.security.core.annotation.AuthenticationPrincipal; // ★追加: AuthenticationPrincipal用
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository; // ★追加: OAuth2クライアント情報用
import org.springframework.security.oauth2.client.registration.ClientRegistration; // ★追加: ClientRegistration用
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder; // ★追加: URL構築用

import jakarta.validation.Valid;
import java.net.URI; // URIをimport
import java.util.List;
import java.util.Map; // Mapをimport (レスポンスボディ用)
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;
    private final AppUserRepository appUserRepository; // JWT認証ユーザーのAppUser取得用
    private final ClientRegistrationRepository clientRegistrationRepository; // ★追加

    public ReminderController(ReminderService reminderService,
            AppUserRepository appUserRepository,
            ClientRegistrationRepository clientRegistrationRepository) { // ★コンストラクタ変更
        this.reminderService = reminderService;
        this.appUserRepository = appUserRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * 現在認証されているユーザーのユーザー名を取得するヘルパーメソッド
     *
     * @return 認証されているユーザーのユーザー名、または認証されていない場合はnull
     */
    // このメソッドは @AuthenticationPrincipal を使うことで不要になるが、既存のコードで使われているため残す
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * 現在のユーザーに紐づく全てのリマインダーを取得します。
     *
     * @param currentUser 現在認証されているAppUserオブジェクト (Spring Securityが自動注入)
     * @return リマインダーのリストとHTTPステータスOK
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReminderResponse>> getAllRemindersForCurrentUser(
            @AuthenticationPrincipal AppUser currentUser) {
        if (currentUser == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED); // 通常は@PreAuthorizeでハンドリングされる
        }
        List<Reminder> reminders = reminderService.getRemindersByAppUser(currentUser); // ★変更: AppUserオブジェクトを直接渡す
        List<ReminderResponse> responses = reminders.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    /**
     * 現在のユーザーに紐づく、特定のIDのリマインダーを取得します。
     *
     * @param id          リマインダーのID
     * @param currentUser 現在認証されているAppUserオブジェクト
     * @return リマインダーデータとHTTPステータスOK、または見つからない場合はNOT_FOUND
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> getReminderByIdForCurrentUser(@PathVariable Long id,
            @AuthenticationPrincipal AppUser currentUser) {
        if (currentUser == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return reminderService.getReminderByIdAndAppUser(id, currentUser) // ★変更: AppUserオブジェクトを直接渡す
                .map(this::convertToDto)
                .map(reminder -> new ResponseEntity<>(reminder, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * 新しいリマインダーを作成します。
     * Googleカレンダー連携が必要な場合、Google OAuth2認証フローへリダイレクトするためのURLを返します。
     *
     * @param reminderRequest      作成するリマインダーのデータを含むDTO
     * @param linkToGoogleCalendar Googleカレンダーと連携するかどうかのフラグ
     * @param currentUser          現在認証されているAppUserオブジェクト
     * @return 作成されたリマインダーデータまたはリダイレクトURLとHTTPステータス
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> createReminder(
            @Valid @RequestBody ReminderRequest reminderRequest,
            @RequestParam(defaultValue = "false") boolean linkToGoogleCalendar, // ★追加
            @AuthenticationPrincipal AppUser currentUser // ★追加: AppUserオブジェクトを直接取得
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        try {
            // まずリマインダーをDBに仮保存（Google連携の有無にかかわらず）
            // ReminderServiceのcreateReminderメソッドのシグネチャを変更する必要あり
            Reminder reminder = convertToEntity(reminderRequest);
            // ユーザーはここで既に確定しているので、Service層にcurrentUserを渡す必要はない
            // Service層内でgetCurrentAuthenticatedAppUser()を呼ぶか、Controllerから直接渡すか
            // 今回はControllerから渡す方式に統一
            reminder.setAppUser(currentUser);
            Reminder savedReminder = reminderService.createReminder(reminder, linkToGoogleCalendar); // ★変更

            if (linkToGoogleCalendar) {
                // Google認証フローを開始するためのリダイレクトURLを生成
                ClientRegistration googleRegistration = clientRegistrationRepository.findByRegistrationId("google");
                if (googleRegistration == null) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "Google OAuth2 registration not found."));
                }

                // Spring Securityのデフォルトの認証エンドポイントを利用
                // /oauth2/authorization/{registrationId}
                String authorizationRequestUri = UriComponentsBuilder.fromUriString("/oauth2/authorization/google")
                        // この redirect_uri は、Google 認証後にGoogleがアクセスするバックエンドのURIです。
                        // Spring Securityのデフォルト動作では /oauth2/callback/{registrationId} を期待します。
                        // Spring SecurityのOauth2LoginAuthenticationFilterが処理します。
                        // ただし、stateパラメータでフロントエンドにリダイレクトされるURLを仕込む必要があります。
                        // 現状のSpring Security OAuth2LoginConfigurerの設定に依存します。
                        // simplerなのは、Spring Securityのデフォルトの /oauth2/callback/google に戻った後、
                        // フロントエンドにリダイレクトするように設定することです。
                        // ここでは、一旦Spring Securityのデフォルトコールバックに戻ることを前提とします。
                        // state にはリマインダーIDを埋め込み、Google認証完了後にそのIDを使って処理を継続します。
                        .queryParam("state", savedReminder.getId().toString()) // リマインダーIDをstateとして渡す
                        .build().toUriString();

                // フロントエンドにリダイレクトURLとメッセージを返す
                return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                        "message", "Redirecting to Google for calendar integration.",
                        "redirectUrl", authorizationRequestUri,
                        "reminderId", savedReminder.getId()));
            } else {
                // Google連携が不要な場合は、そのまま成功レスポンスを返す
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "message", "Reminder created successfully.",
                        "reminder", convertToDto(savedReminder)));
            }

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error creating reminder: " + e.getMessage()));
        }
    }

    /**
     * Google OAuth2認証後のリダイレクト先エンドポイント（フロントエンドから呼び出し）
     * このエンドポイントは、Google認証が成功し、バックエンドがトークンを保存した後に、
     * フロントエンドが最終的に呼び出してリマインダーの最終処理を指示するためのものです。
     *
     * @param reminderId  Google認証フロー中にセッションに保存されたリマインダーID
     * @param currentUser 認証済みAppUserオブジェクト
     * @return 処理結果とHTTPステータス
     */
    @GetMapping("/oauth2/callback/finalize-reminder") // ★追加
    @PreAuthorize("isAuthenticated()") // このエンドポイントも認証済みユーザーのみアクセス可能
    public ResponseEntity<Map<String, String>> finalizeReminderAfterGoogleAuth(
            @RequestParam("reminderId") Long reminderId,
            @AuthenticationPrincipal AppUser currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }
        try {
            reminderService.finalizeReminderCreationWithGoogleCalendar(reminderId, currentUser); // ★追加
            return ResponseEntity.ok(Map.of("message", "Reminder created and linked to Google Calendar successfully."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error finalizing reminder: " + e.getMessage()));
        }
    }

    /**
     * 既存のリマインダーを更新します。
     *
     * @param id              更新するリマインダーのID
     * @param reminderRequest 更新データを含むDTO
     * @param currentUser     現在認証されているAppUserオブジェクト
     * @return 更新されたリマインダーデータとHTTPステータスOK、または見つからない場合はNOT_FOUND
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> updateReminder(@PathVariable Long id,
            @Valid @RequestBody ReminderRequest reminderRequest,
            @AuthenticationPrincipal AppUser currentUser // ★追加
    ) {
        if (currentUser == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // updateReminder は Google Calendar と同期するロジックを含む想定
        Reminder updatedReminder = reminderService.updateReminder(id, reminderRequest, currentUser); // ★変更:
                                                                                                     // AppUserオブジェクトを直接渡す

        if (updatedReminder != null) {
            return new ResponseEntity<>(convertToDto(updatedReminder), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * リマインダーを削除します。
     *
     * @param id          削除するリマインダーのID
     * @param currentUser 現在認証されているAppUserオブジェクト
     * @return HTTPステータスNO_CONTENT（削除成功）、またはNOT_FOUND
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) { // ★追加
        if (currentUser == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // deleteReminder は Google Calendar と同期するロジックを含む想定
        boolean deleted = reminderService.deleteReminder(id, currentUser); // ★変更: AppUserオブジェクトを直接渡す

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