package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.GoogleAuthToken; // GoogleAuthToken import
import com.example.google.google_hackathon.dto.ReminderRequest; // ReminderRequest import
import com.example.google.google_hackathon.repository.ReminderRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository; // GoogleAuthTokenRepository import

import org.springframework.security.core.Authentication; // Authentication import
import org.springframework.security.core.context.SecurityContextHolder; // SecurityContextHolder import
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession; // HttpSession import
import org.springframework.web.context.request.RequestContextHolder; // RequestContextHolder import
import org.springframework.web.context.request.ServletRequestAttributes; // ServletRequestAttributes import

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final GoogleAuthTokenRepository googleAuthTokenRepository; // ★追加
    private final GoogleCalendarService googleCalendarService; // ★追加

    public ReminderService(ReminderRepository reminderRepository,
            GoogleAuthTokenRepository googleAuthTokenRepository, // ★追加
            GoogleCalendarService googleCalendarService) { // ★追加
        this.reminderRepository = reminderRepository;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.googleCalendarService = googleCalendarService;
    }

    /**
     * 現在のユーザーに紐づく全てのリマインダーを取得します。
     *
     * @param appUser 現在認証されているAppUserオブジェクト
     * @return リマインダーのリスト
     */
    public List<Reminder> getRemindersByAppUser(AppUser appUser) { // ★変更: usernameからAppUserへ
        return reminderRepository.findByAppUser(appUser); // ★変更: findByUsernameからfindByAppUserへ
    }

    /**
     * 特定のIDのリマインダーが現在のユーザーに紐づくか確認し、取得します。
     *
     * @param id      リマインダーのID
     * @param appUser 現在認証されているAppUserオブジェクト
     * @return リマインダーOptional
     */
    public Optional<Reminder> getReminderByIdAndAppUser(Long id, AppUser appUser) { // ★変更: usernameからAppUserへ
        return reminderRepository.findByIdAndAppUser(id, appUser); // ★変更: findByIdAndUsernameからfindByIdAndAppUserへ
    }

    /**
     * 新しいリマインダーを作成します。Googleカレンダー連携の可否を考慮。
     *
     * @param reminder             作成するリマインダーエンティティ（AppUserが既にセットされている前提）
     * @param linkToGoogleCalendar Googleカレンダーと連携するかどうかのフラグ
     * @return 作成されたリマインダー
     */
    @Transactional
    public Reminder createReminder(Reminder reminder, boolean linkToGoogleCalendar) { // ★変更
        // AppUserはControllerで設定済みなので、ここでは設定しない
        Reminder savedReminder = reminderRepository.save(reminder);

        // ここではGoogleカレンダー連携は行わない。
        // Google認証後の /oauth2/callback/finalize-reminder エンドポイントで処理される。
        // linkToGoogleCalendar フラグはControllerでリダイレクト判断にのみ利用される。

        return savedReminder;
    }

    /**
     * Google OAuth2認証後のリマインダー保存およびGoogleカレンダー連携の最終処理。
     *
     * @param reminderId  処理するリマインダーのID
     * @param currentUser 認証済みのAppUser
     */
    @Transactional
    public void finalizeReminderCreationWithGoogleCalendar(Long reminderId, AppUser currentUser) { // ★追加
        Optional<Reminder> reminderOpt = reminderRepository.findById(reminderId);
        if (reminderOpt.isPresent()) {
            Reminder reminder = reminderOpt.get();
            // リマインダーが現在のユーザーに属するか確認
            if (!reminder.getAppUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("User not authorized to finalize this reminder.");
            }
            // Googleカレンダー連携サービスを呼び出す
            googleCalendarService.createGoogleCalendarEvent(reminder, currentUser);
        } else {
            throw new IllegalArgumentException("Reminder not found with ID: " + reminderId);
        }
    }

    /**
     * 既存のリマインダーを更新します。
     *
     * @param id              更新するリマインダーのID
     * @param reminderRequest 更新データを含むDTO
     * @param currentUser     現在認証されているAppUserオブジェクト
     * @return 更新されたリマインダー、または見つからない場合はnull
     */
    @Transactional
    public Reminder updateReminder(Long id, ReminderRequest reminderRequest, AppUser currentUser) { // ★変更
        return reminderRepository.findByIdAndAppUser(id, currentUser) // ★変更: usernameからAppUserへ
                .map(existingReminder -> {
                    // 他のフィールドを更新
                    existingReminder.setCustomTitle(reminderRequest.getCustomTitle());
                    existingReminder.setRemindDate(reminderRequest.getRemindDate());
                    existingReminder.setRemindTime(reminderRequest.getRemindTime());
                    existingReminder.setDescription(reminderRequest.getDescription());
                    existingReminder.setIsCompleted(
                            reminderRequest.getIsCompleted() != null ? reminderRequest.getIsCompleted() : false);
                    existingReminder.setUpdatedAt(LocalDateTime.now()); // 更新日時をセット

                    Reminder updatedReminder = reminderRepository.save(existingReminder);
                    // Googleカレンダーイベントの更新ロジックもここに実装可能
                    // googleCalendarService.updateGoogleCalendarEvent(updatedReminder,
                    // currentUser);
                    return updatedReminder;
                }).orElse(null); // 見つからないか、ユーザーに紐付かない場合
    }

    /**
     * リマインダーを削除します。
     *
     * @param id          削除するリマインダーのID
     * @param currentUser 現在認証されているAppUserオブジェクト
     * @return 削除が成功した場合はtrue、それ以外はfalse
     */
    @Transactional
    public boolean deleteReminder(Long id, AppUser currentUser) { // ★変更
        return reminderRepository.findByIdAndAppUser(id, currentUser) // ★変更: usernameからAppUserへ
                .map(reminder -> {
                    reminderRepository.delete(reminder);
                    // Googleカレンダーイベントの削除ロジックもここに実装可能
                    // googleCalendarService.deleteGoogleCalendarEvent(reminder, currentUser);
                    return true;
                }).orElse(false);
    }
}