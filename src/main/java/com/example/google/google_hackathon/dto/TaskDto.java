package com.example.google.google_hackathon.dto;
import java.time.LocalDate;

// フロントとやり取りするためのデータ構造
public class TaskDto {
    public String id;
    public String title;
    public String assignee;
    public LocalDate planStart;
    public LocalDate planEnd;
    public LocalDate actualStart;
    public LocalDate actualEnd;
    public String status;

    // public フィールドのままでも Jackson は変換してくれる（getter/setter不要）
}
