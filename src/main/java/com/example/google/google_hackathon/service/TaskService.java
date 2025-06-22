package com.example.google.google_hackathon.service;


import com.example.google.google_hackathon.dto.TaskDto;
import com.example.google.google_hackathon.entity.Task;
import com.example.google.google_hackathon.repository.TaskManageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service // DI対象にする（Controllerで注入できる）
public class TaskService {

    // 日付フォーマット定義
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private TaskManageRepository taskManageRepository;

    // DBに保存されている全タスクを取得し、DTOに変換して返す
    public List<TaskDto> getAllTasks() {
        return taskManageRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // フロントから受け取ったDTOリストをエンティティに変換し保存する
    public void updateTasks(List<TaskDto> dtos) {
        List<Task> tasks = dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        taskManageRepository.saveAll(tasks);
    }

    // Entity → DTO の変換
    private TaskDto toDto(Task task) {
        TaskDto dto = new TaskDto();
        dto.id = task.getId();
        dto.title = task.getTitle();
        dto.assignee = task.getAssignee();
        
        // LocalDate型を文字列に変換
        if (task.getPlan_start() != null) {
            dto.plan_start = task.getPlan_start().format(DATE_FORMATTER);
        } else {
            dto.plan_start = "";
        }
        
        if (task.getPlan_end() != null) {
            dto.plan_end = task.getPlan_end().format(DATE_FORMATTER);
        } else {
            dto.plan_end = "";
        }
        
        if (task.getActual_start() != null) {
            dto.actual_start = task.getActual_start().format(DATE_FORMATTER);
        } else {
            dto.actual_start = "";
        }
        
        if (task.getActual_end() != null) {
            dto.actual_end = task.getActual_end().format(DATE_FORMATTER);
        } else {
            dto.actual_end = "";
        }
        
        dto.status = task.getStatus();
        dto.parent_id = task.getParentId();
        return dto;
    }

    // DTO → Entity の変換
    private Task toEntity(TaskDto dto) {
        Task task = new Task();

        // IDがnullでない場合のみ設定
        if (dto.id != null && dto.id > 0) {
            task.setId(dto.id);
        }
        
        task.setTitle(dto.title);
        task.setAssignee(dto.assignee);
        
        // 文字列の日付をLocalDate型に変換
        if (dto.plan_start != null && !dto.plan_start.isEmpty()) {
            task.setPlan_start(parseDate(dto.plan_start));
        }
        
        if (dto.plan_end != null && !dto.plan_end.isEmpty()) {
            task.setPlan_end(parseDate(dto.plan_end));
        }
        
        if (dto.actual_start != null && !dto.actual_start.isEmpty()) {
            task.setActual_start(parseDate(dto.actual_start));
        }
        
        if (dto.actual_end != null && !dto.actual_end.isEmpty()) {
            task.setActual_end(parseDate(dto.actual_end));
        }
        
        task.setStatus(dto.status);
        task.setParentId(dto.parent_id);

        return task;
    }
    
    // 文字列の日付をLocalDate型に変換するユーティリティメソッド
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            // 日付形式が異なる場合はnullを返す
            return null;
        }
    }
}