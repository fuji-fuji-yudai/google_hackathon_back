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

    // @GetMapping
    // public ResponseEntity<List<TaskDto>> getTasks(@RequestHeader("Authorization")
    // String authHeader) {
    // String token = authHeader.replace("Bearer ", "");
    // if (!JwtUtil.validateToken(token)) {
    // return ResponseEntity.status(401).build();
    // }

    // return ResponseEntity.ok(taskService.getAllTasks());
    // }

    // @PostMapping
    // public ResponseEntity<Void> updateTasks(
    // @RequestBody List<TaskDto> tasks,
    // @RequestHeader("Authorization") String authHeader) {
    // String token = authHeader.replace("Bearer ", "");
    // if (!JwtUtil.validateToken(token)) {
    // return ResponseEntity.status(401).build();
    // }

    // taskService.updateTasks(tasks);
    // return ResponseEntity.ok().build();
    // }
    // }

    // TaskManageController.java を以下のようにデバッグ情報を追加

    // @RestController
    // @RequestMapping("/api/tasks")
    // public class TaskManageController {
    // @Autowired
    // private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> getTasks(@RequestHeader("Authorization") String authHeader) {
        System.out.println("=== TaskController デバッグ ===");
        System.out.println("受信したAuthorizationヘッダー: " + authHeader);

        String token = authHeader.replace("Bearer ", "");
        System.out.println("抽出したトークン: " + token.substring(0, Math.min(50, token.length())));

        // JwtUtilの詳細なデバッグ
        try {
            System.out.println("validateToken 実行前...");
            boolean isValid = JwtUtil.validateToken(token);
            System.out.println("validateToken 結果: " + isValid);

            if (!isValid) {
                System.out.println("❌ トークン検証失敗 - 401を返却");
                return ResponseEntity.status(401).build();
            }

            // 追加: ユーザー名も取得してみる
            String username = JwtUtil.getUsernameFromToken(token);
            System.out.println("✅ トークン検証成功 - ユーザー: " + username);

        } catch (Exception e) {
            System.out.println("❌ JwtUtil実行中の例外: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).build();
        }

        System.out.println("タスク一覧を取得中...");
        List<TaskDto> tasks = taskService.getAllTasks();
        System.out.println("取得したタスク数: " + (tasks != null ? tasks.size() : "null"));

        return ResponseEntity.ok(tasks);
    }

    @PostMapping
    public ResponseEntity<Void> updateTasks(
            @RequestBody List<TaskDto> tasks,
            @RequestHeader("Authorization") String authHeader) {

        System.out.println("=== TaskController POST デバッグ ===");
        System.out.println("受信したタスク数: " + (tasks != null ? tasks.size() : "null"));
        System.out.println("受信したAuthorizationヘッダー: " + authHeader);

        String token = authHeader.replace("Bearer ", "");
        System.out.println("抽出したトークン: " + token.substring(0, Math.min(50, token.length())));

        try {
            boolean isValid = JwtUtil.validateToken(token);
            System.out.println("validateToken 結果: " + isValid);

            if (!isValid) {
                System.out.println("❌ トークン検証失敗 - 401を返却");
                return ResponseEntity.status(401).build();
            }

            String username = JwtUtil.getUsernameFromToken(token);
            System.out.println("✅ トークン検証成功 - ユーザー: " + username);

        } catch (Exception e) {
            System.out.println("❌ JwtUtil実行中の例外: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).build();
        }

        System.out.println("タスク更新を実行中...");
        taskService.updateTasks(tasks);
        System.out.println("✅ タスク更新完了");

        return ResponseEntity.ok().build();
    }
}
