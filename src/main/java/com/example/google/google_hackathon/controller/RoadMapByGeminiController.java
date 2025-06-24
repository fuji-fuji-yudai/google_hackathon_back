package com.example.google.google_hackathon.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.google.google_hackathon.dto.RoadmapRequestDto;
import com.example.google.google_hackathon.service.GeminiService;

@RestController
@RequestMapping("/api/roadmap")
public class RoadMapByGeminiController {

    @Autowired
    private GeminiService geminiService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateRoadmap(@RequestBody RoadmapRequestDto request) {
        String result = geminiService.generateRoadmapProposal(request.getCategory(), request.getSummaries());
        return ResponseEntity.ok(result);
    }
}
