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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.google.google_hackathon.dto.ReflectionSummaryDtoByFuji;
import com.example.google.google_hackathon.dto.RoadmapRequestDto;
import com.example.google.google_hackathon.dto.RoadmapSuggestRequestDto;
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
    // @Autowired
    // private JwtTokenProvider jwtTokenProvider;



   @PostMapping("/suggest")
public ResponseEntity<String> getSummary(
    @AuthenticationPrincipal UserDetails userDetails,
    @RequestBody RoadmapSuggestRequestDto request
) {
    String userName = userDetails.getUsername();
    Long userId = AppUserService.getUserIdByUsername(userName);
    List<ReflectionSummaryDtoByFuji> result = service.getSummariesByPeriod(userId, request.getPeriod());

    RoadmapRequestDto requestDto = new RoadmapRequestDto();
    requestDto.setCategory(request.getCategory());
    requestDto.setSummaries(result);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<RoadmapRequestDto> httpRequest = new HttpEntity<>(requestDto, headers);

    String roadmapResult = restTemplate.postForObject(
        "https://my-image-14467698004.asia-northeast1.run.app/api/roadmap/generate",
        httpRequest,
        String.class
    );

    return ResponseEntity.ok(roadmapResult);
}

}