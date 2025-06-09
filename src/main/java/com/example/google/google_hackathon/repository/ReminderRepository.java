package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.entity.AppUser; // AppUserエンティティをインポート
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // 特定のユーザー名に紐づく全てのリマインダーを取得する
    // Reminderエンティティの'appUser'フィールドにある'username'フィールドで検索
    List<Reminder> findByAppUser_Username(String username);

    // 特定のユーザー名に紐づく、かつ特定のステータスのリマインダーを取得する
    List<Reminder> findByAppUser_UsernameAndStatus(String username, String status);

    // 特定のIDで、かつ特定のユーザー名に紐づくリマインダーを取得する
    Optional<Reminder> findByIdAndAppUser_Username(Long id, String username);

    // 特定のIDで、かつ特定のユーザー名に紐づくリマインダーを削除する
    void deleteByIdAndAppUser_Username(Long id, String username);

    // 特定のAppUserオブジェクトに紐づくリマインダーを取得
    List<Reminder> findByAppUser(AppUser appUser);
}