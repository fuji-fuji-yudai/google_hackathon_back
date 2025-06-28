package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.dto.ReminderRequest;
import com.example.google.google_hackathon.entity.Reminder;
import com.example.google.google_hackathon.repository.ReminderRepository;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    public Optional<Reminder> getReminderByIdAndUsername(Long id, String username) {

        return reminderRepository.findByIdAndAppUser_Username(id, username);
    }

    @Transactional
    public Reminder createReminder(Reminder reminder) {
        return reminderRepository.save(reminder);
    }

    @Transactional
    public Reminder updateReminder(Long id, ReminderRequest reminderRequest, String username) {
        Optional<Reminder> existingReminderOpt = reminderRepository.findByIdAndAppUser_Username(id, username);

        if (existingReminderOpt.isPresent()) {
            Reminder existingReminder = existingReminderOpt.get();
            existingReminder.setCustomTitle(reminderRequest.getCustomTitle());
            existingReminder.setDescription(reminderRequest.getDescription());

            // String から LocalDate/LocalTime への変換
            if (reminderRequest.getRemindDate() != null && !reminderRequest.getRemindDate().isEmpty()) {
                existingReminder.setRemindDate(LocalDate.parse(reminderRequest.getRemindDate()));
            }
            if (reminderRequest.getRemindTime() != null && !reminderRequest.getRemindTime().isEmpty()) {
                try {
                    existingReminder.setRemindTime(
                            LocalTime.parse(reminderRequest.getRemindTime(), DateTimeFormatter.ofPattern("HH:mm")));
                } catch (DateTimeParseException e) {
                    // "HH:mm:ss" 形式も試す、またはエラーハンドリング
                    existingReminder.setRemindTime(
                            LocalTime.parse(reminderRequest.getRemindTime(), DateTimeFormatter.ISO_LOCAL_TIME));
                }
            }

            // isCompleted の更新
            if (reminderRequest.getStatus() != null) {
                existingReminder.setIsCompleted("COMPLETED".equalsIgnoreCase(reminderRequest.getStatus()));
            }

            return reminderRepository.save(existingReminder);
        }
        return null;
    }

    @Transactional
    public boolean deleteReminder(Long id, String username) {
        Optional<Reminder> reminderOpt = reminderRepository.findByIdAndAppUser_Username(id, username);
        if (reminderOpt.isPresent()) {
            reminderRepository.delete(reminderOpt.get());
            return true;
        }
        return false;
    }

}