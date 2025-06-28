package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.service.TaskService;

import jakarta.servlet.http.HttpServletRequest;

import com.example.google.google_hackathon.dto.TaskDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.security.Principal;

@RestController
// Spring Framework において、HTTPリクエストとコントローラのメソッドやクラスを紐づけるアノテーションです。
// 「このURLにアクセスしたら、この処理を実行してね」と指定するためのものです。
@RequestMapping("/api/tasks")

public class TaskManageController {
    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> getTasks(Principal principal, HttpServletRequest request) {
        System.out.println("=== コントローラー内のデバッグ ===");
        System.out.println("Principal: " + principal);
        System.out.println("Principal名前: " + (principal != null ? principal.getName() : "null"));
        System.out.println("SecurityContext認証: " + SecurityContextHolder.getContext().getAuthentication());
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("================================");

        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @PostMapping
    public ResponseEntity<Void> updateTasks(@RequestBody List<TaskDto> tasks, Principal principal) {
        System.out.println("認証済みユーザー: " + principal.getName());
        System.out.println("受信したタスク数: " + tasks.size());

        // // 各タスクのparent_idを確認
        // for (TaskDto task : tasks) {
        //     System.out.println("タスク: " + task.getTitle() + ", parent_id: " + task.getParentId());
        // }
        taskService.updateTasks(tasks);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Integer id, 
            @RequestParam(defaultValue = "false") boolean deleteChildren,
            Principal principal) {
        
        System.out.println("=== タスク削除リクエスト ===");
        System.out.println("認証済みユーザー: " + principal.getName());
        System.out.println("削除対象ID: " + id);
        System.out.println("子タスクも削除: " + deleteChildren);
        System.out.println("==============================");
        
        taskService.deleteTask(id, deleteChildren);
        return ResponseEntity.ok().build();
    }
}