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

    // タスクを削除する
    public void deleteTask(Integer taskId, boolean deleteChildren) {
        if (taskId == null) {
            throw new IllegalArgumentException("タスクIDがnullです");
        }

        // 削除対象のタスクが存在するかチェック
        if (!taskManageRepository.existsById(taskId)) {
            throw new IllegalArgumentException("指定されたタスクが存在しません: " + taskId);
        }

        if (deleteChildren) {
            // 子タスクも含めて全て削除
            deleteTaskWithChildren(taskId);
        } else {
            // 子タスクの親IDをnullにしてから親タスクを削除
            deleteTaskWithoutChildren(taskId);
        }

        System.out.println("タスク削除完了: ID " + taskId + " (子タスクも削除: " + deleteChildren + ")");
    }

    // 子タスクも含めて再帰的に削除
    private void deleteTaskWithChildren(Integer taskId) {
        // 子タスクを取得
        List<Task> childTasks = taskManageRepository.findByParentId(taskId);
        
        // 子タスクがある場合は再帰的に削除
        for (Task child : childTasks) {
            deleteTaskWithChildren(child.getId());
        }
        
        // 最後に自分自身を削除
        taskManageRepository.deleteById(taskId);
        System.out.println("タスク削除: ID " + taskId);
    }

    // 子タスクの親IDをnullにしてから親タスクを削除
    private void deleteTaskWithoutChildren(Integer taskId) {
        // 子タスクを取得
        List<Task> childTasks = taskManageRepository.findByParentId(taskId);
        
        // 子タスクの親IDをnullに設定
        for (Task child : childTasks) {
            child.setParentId(null);
            System.out.println("子タスクの親ID解除: " + child.getTitle() + " (ID: " + child.getId() + ")");
        }
        
        // 子タスクを保存
        if (!childTasks.isEmpty()) {
            taskManageRepository.saveAll(childTasks);
        }
        
        // 親タスクを削除
        taskManageRepository.deleteById(taskId);
        System.out.println("親タスク削除: ID " + taskId);
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
        
        // 追加: tmp_id の設定
        dto.tmp_id = task.getTmpId();
        // tmp_parent_id はDTOからEntityへの変換時のみ使用するため、ここでは設定しない
        
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
        
        // 追加: tmp_id の設定
        task.setTmpId(dto.tmp_id);

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