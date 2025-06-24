package com.example.google.google_hackathon.controller;

import java.io.Console;
import java.util.List;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.google.google_hackathon.dto.ReflectionSummaryDtoByFuji;
import com.example.google.google_hackathon.dto.RoadmapRequestDto;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import com.example.google.google_hackathon.security.JwtUtil;
import com.example.google.google_hackathon.service.AppUserService;
import com.example.google.google_hackathon.service.ReflectionSummaryService;

@RestController
@RequestMapping("/api/reflections")
public class ReflectionSummaryControllerByFuji {

    @Autowired
    private ReflectionSummaryService service;

    
    @Autowired
    private AppUserService AppUserService;
    
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;



    @GetMapping("/suggest")
    public ResponseEntity<String> getSummary(
        @Header("Authorization") String authHeader,
        @RequestParam String period,
        @RequestParam String category
    ) {
        String userName = null;
        String token = authHeader.replace("Bearer ", "");
        System.out.println("suggest来てます。トークンはこちら！！"+token);
        if (JwtUtil.validateToken(token)) {
            userName = jwtTokenProvider.getUsernameFromToken(token);
        } else {
            userName = "null";
        }
        Long userId = AppUserService.getUserIdByUsername(userName);
        List<ReflectionSummaryDtoByFuji> result = service.getSummariesByPeriod(userId, period);

        System.out.println("振替りはゲット！");
        // RoadmapRequestDto を作成
        RoadmapRequestDto requestDto = new RoadmapRequestDto();
        requestDto.setCategory(category);
        requestDto.setSummaries(result);

        
        // API呼び出し
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RoadmapRequestDto> request = new HttpEntity<>(requestDto, headers);
        String roadmapResult = restTemplate.postForObject("https://my-image-14467698004.asia-northeast1.run.app/api/roadmap/generate", request, String.class);
        return ResponseEntity.ok(roadmapResult);
    }
}