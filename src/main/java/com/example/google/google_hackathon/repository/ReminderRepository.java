package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional; // Optional を使用しているためインポートが必要

/**
 * ReminderRepository は Reminder エンティティに対応する
 * データベース操作を行うためのインターフェースです。
 * * JpaRepository を継承しているため、標準的な操作（findAll, save, delete など）が自動で使えます。
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /**
     * AppUserのusernameに紐づく全てのリマインダーを取得します。
     * Reminderエンティティ内の appUser フィールドと、AppUserエンティティ内の username フィールドを使用します。
     * * @param username ユーザー名
     * 
     * @return ユーザーに紐づくリマインダーのリスト
     */
    List<Reminder> findByAppUser_Username(String username); // ★このメソッドが必要です★

    /**
     * AppUserのusernameに紐づく、かつ特定のステータスのリマインダーを取得します。
     * * @param username ユーザー名
     * 
     * @param status ステータス
     * @return ユーザーに紐づく特定のステータスのリマインダーのリスト
     */
    List<Reminder> findByAppUser_UsernameAndIsCompleted(String username, Boolean isCompleted);

    /**
     * 指定されたIDで、かつAppUserのusernameに紐づくリマインダーを取得します。
     * * @param id 検索するリマインダーのID
     * 
     * @param username ユーザー名
     * @return 該当するリマインダーのOptionalオブジェクト
     */
    Optional<Reminder> findByIdAndAppUser_Username(Long id, String username);
}