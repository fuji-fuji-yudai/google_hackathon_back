package com.example.google.google_hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ReminderRequest {

    @NotBlank(message = "タイトルは必須です。")
    @Size(max = 255, message = "タイトルは255文字以内で入力してください。")
    private String customTitle;

    @Size(max = 1000, message = "説明は1000文字以内で入力してください。")
    private String description;

    @NotBlank(message = "日付は必須です。")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日付の形式が不正です (YYYY-MM-DD)。")
    private String remindDate;

    @NotBlank(message = "時刻は必須です。")
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "時刻の形式が不正です (HH:MM)。")
    private String remindTime;

    private String recurrenceType; // 例: "NONE", "DAILY", "WEEKLY" など

    private String nextRemindTime; // 次のリマインダー時刻 (YYYY-MM-DDTHH:MM形式) ★String型で維持★

    private List<String> attendeeEmails;
}
