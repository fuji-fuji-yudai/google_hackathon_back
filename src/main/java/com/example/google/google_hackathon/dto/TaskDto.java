package com.example.google.google_hackathon.dto;

public class TaskDto {
    public Integer id;
    public String title;
    public String assignee;
    public String plan_start;   // 文字列型で日付を保持
    public String plan_end;     // 文字列型で日付を保持
    public String actual_start; // 文字列型で日付を保持
    public String actual_end;   // 文字列型で日付を保持
    public String status;
    public Integer parent_id;
        // 追加: AI生成時の一時ID用フィールド
    public Integer tmp_id;
    public Integer tmp_parent_id;
}