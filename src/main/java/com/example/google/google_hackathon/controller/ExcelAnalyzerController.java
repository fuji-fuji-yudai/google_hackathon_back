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
@CrossOrigin(origins = "*") // 必要に応じてCORSを設定
public class ExcelAnalyzerController {

    @Autowired
    private ExcelAnalyzerService excelAnalyzerService;

    /**
     * Excelファイルを分析してタスクを生成（プレビュー用）
     */
    @PostMapping("/analyze-excel")
    public ResponseEntity<?> analyzeExcel(
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        try {
            System.out.println("=== Excel分析リクエスト受信 ===");
            System.out.println("ファイル名: " + file.getOriginalFilename());
            System.out.println("ファイルサイズ: " + file.getSize() + " bytes");
            System.out.println("認証済みユーザー: " + (principal != null ? principal.getName() : "未認証"));

            // ファイルの基本検証
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("アップロードされたファイルが空です");
            }

            // Excel分析サービスを呼び出し（DBに保存しない、プレビュー用）
            List<TaskDto> tasks = excelAnalyzerService.analyzeExcel(file);

            System.out.println("=== 分析結果 ===");
            System.out.println("生成されたタスク数: " + tasks.size());
            
            // 階層構造の確認
            long phaseTaskCount = tasks.stream().filter(t -> t.tmp_parent_id == null).count();
            long childTaskCount = tasks.stream().filter(t -> t.tmp_parent_id != null).count();
            System.out.println("フェーズタスク: " + phaseTaskCount + "個");
            System.out.println("子タスク: " + childTaskCount + "個");

            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            System.err.println("=== Excel分析エラー ===");
            System.err.println("エラーメッセージ: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Excel分析中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * 分析済みタスクをDBにインポート
     */
    @PostMapping("/import-excel-tasks")
    public ResponseEntity<?> importExcelTasks(
            @RequestBody List<TaskDto> tasks,
            Principal principal) {

        try {
            System.out.println("=== Excelタスクインポート開始 ===");
            System.out.println("認証済みユーザー: " + (principal != null ? principal.getName() : "未認証"));
            System.out.println("インポート対象タスク数: " + tasks.size());

            // リクエストデータの妥当性チェック
            if (tasks == null || tasks.isEmpty()) {
                return ResponseEntity.badRequest().body("インポートするタスクがありません");
            }

            // 受信したJSONデータを詳細表示
            System.out.println("=== 受信データ詳細 ===");
            for (int i = 0; i < Math.min(tasks.size(), 5); i++) { // 最初の5件のみ詳細表示
                TaskDto task = tasks.get(i);
                System.out.println(String.format("タスク%d:", i + 1));
                System.out.println(String.format("  ID: %s", task.id));
                System.out.println(String.format("  タイトル: %s", task.title));
                System.out.println(String.format("  担当者: %s", task.assignee));
                System.out.println(String.format("  予定開始: %s", task.plan_start));
                System.out.println(String.format("  予定終了: %s", task.plan_end));
                System.out.println(String.format("  実績開始: %s", task.actual_start));
                System.out.println(String.format("  実績終了: %s", task.actual_end));
                System.out.println(String.format("  ステータス: %s", task.status));
                System.out.println(String.format("  親ID: %s", task.parent_id));
                System.out.println(String.format("  tmp_ID: %s", task.tmp_id));
                System.out.println(String.format("  tmp_親ID: %s", task.tmp_parent_id));
                System.out.println("---");
            }
            
            if (tasks.size() > 5) {
                System.out.println("... 他 " + (tasks.size() - 5) + " 件のタスク");
            }

            // 階層構造の確認
            long phaseTaskCount = tasks.stream().filter(t -> t.tmp_parent_id == null).count();
            long childTaskCount = tasks.stream().filter(t -> t.tmp_parent_id != null).count();
            System.out.println("=== 階層構造チェック ===");
            System.out.println("フェーズタスク: " + phaseTaskCount + "個");
            System.out.println("子タスク: " + childTaskCount + "個");

            // タスクをDBに保存
            List<TaskDto> savedTasks = excelAnalyzerService.saveExcelTasks(tasks);

            System.out.println("=== DBに保存されたタスク数: " + savedTasks.size() + " ===");

            return ResponseEntity.ok(savedTasks);
        } catch (Exception e) {
            System.err.println("=== Excelタスクインポートエラー ===");
            System.err.println("エラーメッセージ: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Excelタスクのインポート中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * ヘルスチェック用エンドポイント
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Excel Analyzer Service is running");
    }
}