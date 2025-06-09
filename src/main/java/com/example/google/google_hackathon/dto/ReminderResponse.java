package com.example.google.google_hackathon.dto;
//リマインダーの出力用DTO

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReminderResponse {
    private Long id;
    private String customTitle;
    private LocalDate remindDate;
    private LocalTime remindTime;
    private String description;
    private String status;
    private String username; // 必要に応じて、誰のリマインダーか示すために含める
}