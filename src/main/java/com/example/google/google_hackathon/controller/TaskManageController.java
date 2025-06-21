package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.service.TaskService;
import com.example.google.google_hackathon.dto.TaskDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.google.google_hackathon.security.JwtUtil;
import java.util.List;

@RestController
// Spring Framework において、HTTPリクエストとコントローラのメソッドやクラスを紐づけるアノテーションです。
// 「このURLにアクセスしたら、この処理を実行してね」と指定するためのものです。
@RequestMapping("/api/tasks")

public class TaskManageController {
    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> getTasks(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        if (!JwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @PostMapping
    public ResponseEntity<Void> updateTasks(
            @RequestBody List<TaskDto> tasks,
            @RequestHeader("Authorization") String authHeader) {
                System.out.println("受け取った Authorization ヘッダー: " + authHeader); // ← ここ
        String token = authHeader.replace("Bearer ", "");
        if (!JwtUtil.validateToken(token)) {
            System.out.println("JWTが無効: " + token); // ← ここ
            return ResponseEntity.status(401).build();
        }

        taskService.updateTasks(tasks);
        return ResponseEntity.ok().build();
    }
}
