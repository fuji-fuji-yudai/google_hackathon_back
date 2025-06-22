package com.example.google.google_hackathon.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "tasks", schema = "public")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // ← この行を追加
    private Long id;

    private String title;
    private String assignee;
    private LocalDate plan_start;
    private LocalDate plan_end;
    private LocalDate actual_start;
    private LocalDate actual_end;
    private String status;
}