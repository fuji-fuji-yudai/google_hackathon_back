package com.example.google.google_hackathon.dto;
import java.time.LocalDate;

// フロントとやり取りするためのデータ構造
public class TaskDto {
    public Long id;
    public String title;
    public String assignee;
    public LocalDate plan_start;
    public LocalDate plan_end;
    public LocalDate actual_start;
    public LocalDate actual_end;
    public String status;

    // public フィールドのままでも Jackson は変換してくれる（getter/setter不要）
}
