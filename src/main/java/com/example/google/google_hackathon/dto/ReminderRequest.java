package com.example.google.google_hackathon.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class ReminderRequest {
    @NotBlank(message = "タイトルは必須です。")
    private String customTitle;

    private String description;

    @NotBlank(message = "日付は必須です。")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日付はYYYY-MM-DD形式である必要があります。")
    private String remindDate;

    @NotBlank(message = "時刻は必須です。")
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "時刻はHH:MM形式である必要があります。") // HH:MM:SS も考慮するならパターン変更
    private String remindTime;

    private String status; // 例: PENDING, COMPLETED
}