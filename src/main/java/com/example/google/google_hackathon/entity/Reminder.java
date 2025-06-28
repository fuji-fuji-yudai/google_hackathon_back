package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminders", schema = "public") // テーブル名を適切に設定
@Data
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser appUser; // AppUserエンティティへの関連

    @Column(name = "custom_title", nullable = false)
    private String customTitle;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "remind_date", nullable = false)
    private LocalDate remindDate;

    @Column(name = "remind_time", nullable = false)
    private LocalTime remindTime;

    @Column(name = "recurrence_type")
    private String recurrenceType; // 例: "NONE", "DAILY", "WEEKLY"

    @Column(name = "next_remind_time")
    private LocalDateTime nextRemindTime;

    @Column(name = "google_event_id")
    private String googleEventId; // Google Calendar Event ID

}