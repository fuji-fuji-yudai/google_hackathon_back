package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    // AppUserに基づいてリマインダーを検索するメソッドを追加
    List<Reminder> findByAppUser(AppUser appUser);
}