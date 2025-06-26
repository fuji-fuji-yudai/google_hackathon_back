package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser; // AppUserエンティティをインポート
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.dto.ReminderRequest; // ReminderRequest DTOをインポート
import com.example.google.google_hackathon.repository.ReminderRepository;
import com.example.google.google_hackathon.repository.AppUserRepository; // AppUserRepositoryをインポート

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final AppUserRepository appUserRepository; // AppUserRepositoryを注入

    // コンストラクタインジェクション（@Autowiredよりも推奨）
    public ReminderService(ReminderRepository reminderRepository, AppUserRepository appUserRepository) {
        this.reminderRepository = reminderRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * 特定のユーザー名に紐づく全てのリマインダーを取得します。
     *
     * @param username ユーザー名
     * @return ユーザーに紐づくリマインダーのリスト
     */
    public List<Reminder> getRemindersByUsername(String username) {
        return reminderRepository.findByAppUser_Username(username);
    }

    /**
     * 特定のユーザー名に紐づく、かつ特定の完了ステータスのリマインダーを取得します。
     * ★変更: status を isCompleted に変更し、メソッド名も調整
     * 
     * @param username    ユーザー名
     * @param isCompleted 完了ステータス (true: 完了, false: 未完了)
     * @return ユーザーに紐づく特定のステータスのリマインダーのリスト
     */
    public List<Reminder> getRemindersByUsernameAndIsCompleted(String username, Boolean isCompleted) {
        return reminderRepository.findByAppUser_UsernameAndIsCompleted(username, isCompleted);
    }

    /**
     * 指定されたIDで、かつ特定のユーザー名に紐づくリマインダーを取得します。
     *
     * @param id       検索するリマインダーのID
     * @param username ユーザー名
     * @return 該当するリマインダーのOptionalオブジェクト
     */
    public Optional<Reminder> getReminderByIdAndUsername(Long id, String username) {
        return reminderRepository.findByIdAndAppUser_Username(id, username);
    }

    /**
     * 新しいリマインダーを作成し、データベースに保存します。
     * リマインダーエンティティには、Controller側で既にAppUserが設定されていることを想定します。
     *
     * @param reminder 作成するリマインダーオブジェクト（AppUserが設定済み）
     * @return 保存されたリマインダーオブジェクト
     */
    @Transactional
    public Reminder createReminder(Reminder reminder) {
        // AppUserはControllerで取得され、Reminderエンティティに設定されているべきです。
        // ここで改めて取得する必要はありませんが、念のためAppUserが設定されているか確認しても良いでしょう。
        if (reminder.getAppUser() == null || reminder.getAppUser().getId() == null) {
            throw new IllegalArgumentException("Reminder must be associated with an existing AppUser.");
        }
        return reminderRepository.save(reminder);
    }

    /**
     * 既存のリマインダーを更新します。ユーザーの所有権を確認します。
     *
     * @param id              更新するリマインダーのID
     * @param reminderRequest 更新内容を含むDTO
     * @param username        現在認証されているユーザー名
     * @return 更新されたリマインダーオブジェクト、または見つからない/所有者でない場合はnull
     */
    @Transactional
    public Reminder updateReminder(Long id, ReminderRequest reminderRequest, String username) {
        // IDとユーザー名でリマインダーを取得し、ユーザーの所有権を確認
        return reminderRepository.findByIdAndAppUser_Username(id, username)
                .map(existingReminder -> {
                    // DTOからエンティティにデータをコピー
                    // Lombokの@Dataを使っているので、setterは自動生成されています。
                    existingReminder.setCustomTitle(reminderRequest.getCustomTitle());
                    existingReminder.setRemindDate(reminderRequest.getRemindDate());
                    existingReminder.setRemindTime(reminderRequest.getRemindTime());
                    existingReminder.setDescription(reminderRequest.getDescription());

                    // ★修正: リクエストDTOのisCompletedを使用し、Boolean型で設定
                    if (reminderRequest.getIsCompleted() != null) { // isCompletedはBoolean型
                        existingReminder.setIsCompleted(reminderRequest.getIsCompleted());
                    }

                    return reminderRepository.save(existingReminder);
                })
                .orElse(null); // 見つからないか、所有者でない場合はnullを返す
    }

    /**
     * 指定されたIDで、かつ特定のユーザー名に紐づくリマインダーを削除します。
     *
     * @param id       削除するリマインダーのID
     * @param username ユーザー名
     * @return 削除が成功したかどうか (true: 成功, false: 失敗/見つからない)
     */
    @Transactional
    public boolean deleteReminder(Long id, String username) {
        // IDとユーザー名でリマインダーを取得し、ユーザーの所有権を確認
        Optional<Reminder> reminderToDelete = reminderRepository.findByIdAndAppUser_Username(id, username);
        if (reminderToDelete.isPresent()) {
            reminderRepository.delete(reminderToDelete.get());
            return true; // 削除成功
        }
        return false; // 見つからないか、所有者でないため削除失敗
    }
}