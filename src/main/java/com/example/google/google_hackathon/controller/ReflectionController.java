package com.example.google.google_hackathon.controller;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
  @Autowired
  public ReflectionController(ReflectionService reflectionService) {
    this.reflectionService = reflectionService;
  }
  @PostMapping("/create")
  public ReflectionEntity createReflection(@RequestBody ReflectionEntity reflectionEntity, @RequestHeader("Authorization") String authHeader) {
    System.out.println(
      "RequestBody: {" 
      + reflectionEntity.getUserID() + "," 
      + reflectionEntity.getDate() + ","
      + reflectionEntity.geActivity() + ","
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
}
