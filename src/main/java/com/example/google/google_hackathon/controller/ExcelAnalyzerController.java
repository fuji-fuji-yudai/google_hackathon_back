package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.TaskDto;
import com.example.google.google_hackathon.service.ExcelAnalyzerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class ExcelAnalyzerController {

    @Autowired
    private ExcelAnalyzerService excelAnalyzerService;

    @PostMapping("/analyze-excel")
    public ResponseEntity<?> analyzeExcel(
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        try {
            System.out.println("Excel分析リクエスト受信: " + file.getOriginalFilename());
            System.out.println("認証済みユーザー: " + principal.getName());

            // Excel分析サービスを呼び出し（DBに保存しない、プレビュー用）
            List<TaskDto> tasks = excelAnalyzerService.analyzeExcel(file);

            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            System.err.println("Excel分析エラー: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Excel分析中にエラーが発生しました: " + e.getMessage());
        }
    }

    @PostMapping("/import-excel-tasks")
    public ResponseEntity<?> importExcelTasks(
            @RequestBody List<TaskDto> tasks,
            Principal principal) {

        try {
            System.out.println("=== Excelタスクインポート開始 ===");
            System.out.println("認証済みユーザー: " + principal.getName());
            System.out.println("インポート対象タスク数: " + tasks.size());

            // 各タスクの詳細をログ出力
            for (int i = 0; i < tasks.size(); i++) {
                TaskDto task = tasks.get(i);
                System.out.println(String.format("タスク%d: ID=%s, タイトル=%s, 親ID=%s",
                        i + 1, task.id, task.title, task.parent_id));
            }

            // タスクをDBに保存
            List<TaskDto> savedTasks = excelAnalyzerService.saveExcelTasks(tasks);

            System.out.println("=== DBに保存されたタスク数: " + savedTasks.size() + " ===");

            return ResponseEntity.ok(savedTasks);
        } catch (Exception e) {
            System.err.println("Excelタスクインポートエラー: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Excelタスクのインポート中にエラーが発生しました: " + e.getMessage());
        }
    }
}