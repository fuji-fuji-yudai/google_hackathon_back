package com.example.google.google_hackathon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDto {
    private Long id;
    private String customTitle;
    private String description;
    private String remindDate;
    private String remindTime;
    private String recurrenceType;
    private String nextRemindTime;
    private String googleEventId; // Google Calendar Event ID
}