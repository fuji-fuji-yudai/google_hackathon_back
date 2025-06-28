package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.repository.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Transactional
    public Reminder createReminder(Reminder reminder) {
        return reminderRepository.save(reminder);
    }

    @Transactional(readOnly = true)
    public Optional<Reminder> getReminderById(Long id) {
        return reminderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Reminder> getRemindersByUser(AppUser appUser) {
        return reminderRepository.findByAppUser(appUser);
    }

    @Transactional
    public Reminder updateReminder(Reminder reminder) { // ★引数をReminderエンティティに変更★
        // ここではIDが存在する既存のリマインダーを更新することを想定
        if (!reminderRepository.existsById(reminder.getId())) {
            throw new IllegalArgumentException("Reminder with ID " + reminder.getId() + " not found.");
        }
        return reminderRepository.save(reminder);
    }

    @Transactional
    public void deleteReminder(Long id) { // ★引数をLong idに変更★
        reminderRepository.deleteById(id);
    }

    // 必要に応じて、Reminderを検索する他のメソッドなどを追加できます
}