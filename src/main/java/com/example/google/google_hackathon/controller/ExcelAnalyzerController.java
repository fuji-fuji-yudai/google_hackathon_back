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
            
            // Excel分析サービスを呼び出し
            List<TaskDto> tasks = excelAnalyzerService.analyzeExcel(file);
            
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            System.err.println("Excel分析エラー: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Excel分析中にエラーが発生しました: " + e.getMessage());
        }
    }
}