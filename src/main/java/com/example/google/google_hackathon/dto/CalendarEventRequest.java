package com.example.google.google_hackathon.dto;

import lombok.Data; // Lombokのアノテーションを使用

@Data // Lombokのアノテーションでgetter, setterなどを自動生成
public class CalendarEventRequest {
    private String summary;
    private String description;
    private String startDateTimeStr;
    private String endDateTimeStr;
    private String timeZone; // 例: "Asia/Tokyo", "America/New_York"

}