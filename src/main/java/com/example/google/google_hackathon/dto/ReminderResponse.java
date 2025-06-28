package com.example.google.google_hackathon.dto;

import lombok.Data;

@Data
public class ReminderResponse {
    private Long id;
    private String customTitle;
    private String description;
    private String remindDate;
    private String remindTime;
    private String status;
    private String username;
    private String googleEventId;
}