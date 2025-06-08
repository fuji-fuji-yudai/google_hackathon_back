package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.QuestionRequest;
import com.example.google.google_hackathon.service.GeminiService;
import com.example.google.google_hackathon.service.SimilarityService;
import com.example.google.google_hackathon.service.SimilarityService.SimilarMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/gemini")
public class QuestionController {

    @Autowired
    private SimilarityService similarityService;

    @Autowired
    private GeminiService geminiService;

    @PostMapping("/ask")
public ResponseEntity<Map<String, String>> askQuestion(@RequestBody QuestionRequest request) {
    try {
        List<SimilarMessage> topMessages = similarityService.findSimilarMessages(request.getQuestion(), 5);
        List<String> messages = topMessages.stream()
                .map(SimilarMessage::getMessage)
                .collect(Collectors.toList());

        String answer = geminiService.generateAnswer(request.getQuestion(), messages);
        return ResponseEntity.ok(Map.of("answer", answer));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "エラーが発生しました: " + e.getMessage()));
    }
}}


       
