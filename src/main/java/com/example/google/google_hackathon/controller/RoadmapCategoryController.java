package com.example.google.google_hackathon.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.google.google_hackathon.service.AppUserService;
import com.example.google.google_hackathon.service.RoadmapCategoryService;

@RestController
@RequestMapping("/api/categories")
public class RoadmapCategoryController {
    @Autowired
    private RoadmapCategoryService service;
    @Autowired
    private AppUserService AppUserService;

    @GetMapping
    public ResponseEntity<List<String>> getCategories(@AuthenticationPrincipal UserDetails userDetails) {
    String userName = userDetails.getUsername();
    Long userId = AppUserService.getUserIdByUsername(userName);
    List<String> categories = service.getUniqueCategoryNames(userId);
    return ResponseEntity.ok(categories);
}

}
