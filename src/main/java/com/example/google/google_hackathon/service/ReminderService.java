package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.repository.ReminderRepository;
import com.example.google.google_hackathon.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final AppUserRepository appUserRepository;

    public ReminderService(ReminderRepository reminderRepository, AppUserRepository appUserRepository) {
        this.reminderRepository = reminderRepository;
        this.appUserRepository = appUserRepository;
    }

    public List<Reminder> getRemindersByUsername(String username) {
        return reminderRepository.findByAppUser_Username(username);
    }

    public List<Reminder> getRemindersByUsernameAndIsCompleted(String username, Boolean isCompleted) {
        return reminderRepository.findByAppUser_UsernameAndIsCompleted(username, isCompleted);
    }

    public Optional<Reminder> getReminderByIdAndUsername(Long id, String username) {
        return reminderRepository.findByIdAndAppUser_Username(id, username);
    }

    @Transactional
    public Reminder createReminder(Reminder reminder) {
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
        return reminderRepository.findByIdAndAppUser_Username(id, username)
                .map(existingReminder -> {
                    existingReminder.setCustomTitle(reminderRequest.getCustomTitle());
                    existingReminder.setRemindDate(reminderRequest.getRemindDate());
                    existingReminder.setRemindTime(reminderRequest.getRemindTime());
                    existingReminder.setDescription(reminderRequest.getDescription());

                    // ★★★ ここを修正します ★★★
                    // ReminderRequest の status (String) を Reminder エンティティの isCompleted (Boolean)
                    // に変換して設定
                    if (reminderRequest.getStatus() != null) {
                        // "COMPLETED" という文字列であれば true、それ以外（"PENDING"やその他）であれば false とする
                        existingReminder.setIsCompleted("COMPLETED".equalsIgnoreCase(reminderRequest.getStatus()));
                    } else {
                        // status が null の場合は、デフォルトで false に設定するか、既存の値を保持するかを決定
                        // ここでは既存の値を保持するのではなく、明示的に false に設定する例
                        existingReminder.setIsCompleted(false);
                    }

                    return reminderRepository.save(existingReminder);
                })
                .orElse(null);
    }

    @Transactional
    public boolean deleteReminder(Long id, String username) {
        Optional<Reminder> reminderToDelete = reminderRepository.findByIdAndAppUser_Username(id, username);
        if (reminderToDelete.isPresent()) {
            reminderRepository.delete(reminderToDelete.get());
            return true;
        }
        return false;
    }
}