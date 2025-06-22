package com.example.google.google_hackathon.controller;

import java.sql.Date;
import java.sql.Ref;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import com.example.google.google_hackathon.service.reflection.ReflectionService;

@RestController
@RequestMapping("/api/reflection")
public class ReflectionController {
  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  private final ReflectionService reflectionService;
  public ReflectionController(ReflectionService reflectionService) {
    this.reflectionService = reflectionService;
  }
  @GetMapping("/{date}")
  public ResponseEntity<ReflectionEntity> getReflections(
        @PathVariable Date date,
        @RequestHeader("Authorization") String authHeader) {
    System.out.println("抽出したトークン: " + authHeader);
    String token = authHeader.replace("Bearer ", "");
    System.out.println("Authorizationヘッダー: " + authHeader);
    String userName = jwtTokenProvider.getUsernameFromToken(token);
    System.out.println("トークンから取得したユーザー名: " + userName);
    try { 
      ReflectionEntity reflectionEntity = reflectionService.getReflectionsByDate(date, userName);
      return ResponseEntity.ok(reflectionEntity);
    } catch (SQLException e) {
      System.out.println("SQLで例外が発生しました。");
      System.out.println(e.getMessage());
      return null;
    }
  }
  @GetMapping
  public ResponseEntity<List<ReflectionEntity>> getReflections(
        @RequestParam int year,
        @RequestParam int month,
        @RequestHeader("Authorization") String authHeader) {
    System.out.println("抽出したトークン: " + authHeader);
    String token = authHeader.replace("Bearer ", "");
    System.out.println("Authorizationヘッダー: " + authHeader);
    String userName = jwtTokenProvider.getUsernameFromToken(token);
    System.out.println("トークンから取得したユーザー名: " + userName);
    try { 
      List<ReflectionEntity> reflections = reflectionService.getReflectionsByMonth(year, month, userName);
      return ResponseEntity.ok(reflections);
    } catch (SQLException e) {
      System.out.println("SQLで例外が発生しました。");
      System.out.println(e.getMessage());
      return null;
    }
  }
  @PostMapping("/create")
  public ReflectionEntity createReflection(
        @RequestBody ReflectionEntity reflectionEntity, 
        @RequestHeader("Authorization") String authHeader) {
    System.out.println(
      "RequestBody: {" 
      + reflectionEntity.getUserId() + "," 
      + reflectionEntity.getDate() + ","
      + reflectionEntity.getActivity() + ","
      + reflectionEntity.getAchievement() + ","
      + reflectionEntity.getImprovementPoints() + "}"
    );
    System.out.println("抽出したトークン: " + authHeader);
    String token = authHeader.replace("Bearer ", "");
    System.out.println("Authorizationヘッダー: " + authHeader);
    String userName = jwtTokenProvider.getUsernameFromToken(token);
    System.out.println("トークンから取得したユーザー名: " + userName);
    try {
      return reflectionService.createReflection(reflectionEntity, userName);
    } catch (SQLException e) {
      System.out.println("SQLで例外が発生しました。");
      System.out.println(e.getMessage());
      return reflectionEntity;
    }
  }

  @PutMapping("/update/{id}")
  public ResponseEntity<ReflectionEntity> updateReflection(
        @PathVariable Long id,
        @RequestBody ReflectionEntity reflectionEntity) {
    try {
      ReflectionEntity updatedReflection = reflectionService.updateReflection(id, reflectionEntity);
      return ResponseEntity.ok(updatedReflection);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
}
