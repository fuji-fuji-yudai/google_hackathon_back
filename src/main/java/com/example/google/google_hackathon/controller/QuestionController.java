package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.dto.QuestionRequest;
import com.example.google.google_hackathon.dto.SimilarMessageDTO;
import com.example.google.google_hackathon.service.GeminiService;
import com.example.google.google_hackathon.service.SimilarityService;
import com.example.google.google_hackathon.service.SimilarityService.SimilarMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
public ResponseEntity<Map<String, Object>> askQuestion(@RequestBody QuestionRequest request) {
    try {
        String roomId = request.getRoomId();
        List<SimilarMessage> topMessages = similarityService.findSimilarMessages(request.getQuestion(), roomId,5);//似た過去のメッセージを取得
        // List<String> messages = topMessages.stream()
        //         .map(SimilarMessage::getMessage)
        //         .collect(Collectors.toList());
        
List<SimilarMessageDTO> similarDtos = topMessages.stream()
    .map(SimilarMessageDTO::from)
    .collect(Collectors.toList());

String answer = geminiService.generateAnswer(request.getQuestion(),similarDtos);

Map<String, Object> response = new HashMap<>();
response.put("answer", answer);
response.put("similarMessages", similarDtos);

return ResponseEntity.ok(response);

    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "エラーが発生しました: " + e.getMessage()));
    }
}}


       
