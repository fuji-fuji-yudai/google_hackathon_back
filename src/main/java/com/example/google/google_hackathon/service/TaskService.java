package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.dto.TaskDto;
import com.example.google.google_hackathon.entity.Task;
import com.example.google.google_hackathon.repository.TaskManageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service // DI対象にする（Controllerで注入できる）
public class TaskService {

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
        dto.plan_start = task.getPlan_start();
        dto.plan_end = task.getPlan_end();
        dto.actual_start = task.getActual_start();
        dto.actual_end = task.getActual_end();
        dto.status = task.getStatus();
        return dto;
    }

    // DTO → Entity の変換
    private Task toEntity(TaskDto dto) {
        Task task = new Task();

        // IDがnullでない場合のみ設定
        if (dto.id != null && dto.id > 0) {
            task.setId(dto.id);
        }
        // IDがnullの場合は設定しない → PostgreSQLが自動生成

        task.setTitle(dto.title);
        task.setAssignee(dto.assignee);
        task.setPlan_start(dto.plan_start);
        task.setPlan_end(dto.plan_end);
        task.setActual_start(dto.actual_start);
        task.setActual_end(dto.actual_end);
        task.setStatus(dto.status);

        return task;
    }
}
