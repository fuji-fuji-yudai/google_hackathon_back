package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.entity.AppUser; // AppUserをimport
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    // ユーザー名ではなく、AppUserオブジェクトで検索するように変更
    List<Reminder> findByAppUser(AppUser appUser); // ★変更

    Optional<Reminder> findByIdAndAppUser(Long id, AppUser appUser); // ★変更
}