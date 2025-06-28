package com.example.google.google_hackathon.dto;

import lombok.Data; // Lombokを使う場合
import lombok.NoArgsConstructor; // 引数なしコンストラクタを明示的に追加
import lombok.AllArgsConstructor; // 全フィールドコンストラクタを明示的に追加

@Data // @Getter, @Setter, @EqualsAndHashCode, @ToStringを自動生成
@NoArgsConstructor // 引数なしのコンストラクタを自動生成 (必須)
@AllArgsConstructor // 全フィールドを引数とするコンストラクタを自動生成 (任意)
public class CalendarEventRequest {
    private String summary; // イベントタイトル
    private String description; // イベント説明
    private String startDateTimeStr; // 開始日時 (yyyy-MM-dd'T'HH:mm 形式の文字列)
    private String endDateTimeStr; // 終了日時 (yyyy-MM-dd'T'HH:mm 形式の文字列)
    private String timeZone; // タイムゾーン (例: "Asia/Tokyo", "America/New_York")
}