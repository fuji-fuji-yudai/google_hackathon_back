// src/main/java/com/example/google/google_hackathon/dto/CalendarEventRequest.java
package com.example.google.google_hackathon.dto;

import lombok.Data; // Lombokを使う場合

@Data // Lombokのアノテーション
public class CalendarEventRequest {
    private String summary; // イベントタイトル
    private String description; // イベント説明
    private String startDateTimeStr; // 開始日時 (yyyy-MM-dd'T'HH:mm 形式の文字列)
    private String endDateTimeStr; // 終了日時 (yyyy-MM-dd'T'HH:mm 形式の文字列)
    private String timeZone; // タイムゾーン (例: "Asia/Tokyo", "America/New_York")
}