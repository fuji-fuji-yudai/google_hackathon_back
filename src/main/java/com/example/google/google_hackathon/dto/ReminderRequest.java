package com.example.google.google_hackathon.dto;
//リマインダー入力用DTO

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ReminderRequest {
    @NotBlank(message = "タイトルは必須です")
    private String customTitle;

    @NotNull(message = "通知日は必須です")
    private LocalDate remindDate;

    @NotNull(message = "通知時間は必須です")
    private LocalTime remindTime;

    private String description;

    @JsonProperty("status")
    private Boolean isCompleted; // 更新時などに使用するステータス//
}