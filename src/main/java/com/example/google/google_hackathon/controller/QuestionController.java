package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.QuestionRequest;
import com.example.google.google_hackathon.service.GeminiService;
import com.example.google.google_hackathon.service.SimilarityService;
import com.example.google.google_hackathon.service.SimilarityService.SimilarMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/gemini")
public class QuestionController {

    @Autowired
    private SimilarityService similarityService;

    @Autowired
    private GeminiService geminiService;

    @PostMapping("/ask")
    public String askQuestion(@RequestBody QuestionRequest request) {
        try {
            // 1. 類似履歴を取得
            List<SimilarMessage> topMessages = similarityService.findSimilarMessages(request.getQuestion(), 5);

            // 2. メッセージだけ抽出
            List<String> messages = topMessages.stream()
                    .map(SimilarMessage::getMessage)
                    .collect(Collectors.toList());

            // 3. Gemini に送信して回答を得る
            return geminiService.generateAnswer(request.getQuestion(), messages);

        } catch (Exception e) {
            return "エラーが発生しました: " + e.getMessage();
        }
    }
}
