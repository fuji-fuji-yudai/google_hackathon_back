package com.example.google.google_hackathon.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity // JPAエンティティ（DBテーブルとマッピングされる）
@Getter
@Setter
@Table(name = "tasks",schema = "public") // 対応するテーブル名
public class Task {

    @Id // 主キー
    private Long id;

    private String title;      // タスクのタイトル
    private String assignee;   // 担当者
    private LocalDate planStart;  // 計画開始日
    private LocalDate planEnd;    // 計画終了日
    private LocalDate actualStart; // 実績開始日
    private LocalDate actualEnd;   // 実績終了日
    private String status;     // ステータス（例：ToDo, Doing, Done）

    // --- Getter / Setter 省略可能（Lombokを使うと楽） ---
    // 必要なら @Getter @Setter をクラスにつけてもOK
}
