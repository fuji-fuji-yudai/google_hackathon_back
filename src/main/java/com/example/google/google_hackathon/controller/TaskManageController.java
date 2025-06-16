package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.service.TaskService;
import com.example.google.google_hackathon.dto.TaskDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
// Spring Framework において、HTTPリクエストとコントローラのメソッドやクラスを紐づけるアノテーションです。
// 「このURLにアクセスしたら、この処理を実行してね」と指定するためのものです。
@RequestMapping("/api/tasks")

public class TaskManageController {
        @Autowired
    private TaskService taskService;

    @GetMapping
    public List<TaskDto> getTasks() {
        return taskService.getAllTasks();
    }

    @PostMapping
    public ResponseEntity<Void> updateTasks(@RequestBody List<TaskDto> tasks) {
        taskService.updateTasks(tasks);
        return ResponseEntity.ok().build();
    }
}


