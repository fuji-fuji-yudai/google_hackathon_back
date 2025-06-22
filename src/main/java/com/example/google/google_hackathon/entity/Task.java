package com.example.google.google_hackathon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "tasks", schema = "auth")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String title;
    private String assignee;
    private LocalDate plan_start;
    private LocalDate plan_end;
    private LocalDate actual_start;
    private LocalDate actual_end;
    private String status;
    
    // フィールド名を JPA 命名規則に合わせて修正
    // データベースのカラム名は親クラスとの関係を明確にするため snake_case のままにする
    @Column(name = "parent_id")
    private Integer parentId;
}