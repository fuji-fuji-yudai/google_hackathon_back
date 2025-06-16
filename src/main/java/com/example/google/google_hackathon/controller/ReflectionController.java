package com.example.google.google_hackathon.controller;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.service.reflection.ReflectionService;

@RestController
@RequestMapping("/api/reflection")
public class ReflectionController {
  private final ReflectionService reflectionService;
  @Autowired
  public ReflectionController(ReflectionService reflectionService) {
    this.reflectionService = reflectionService;
  }
  @PostMapping("/create")
  public ReflectionEntity createReflection(@RequestBody ReflectionEntity reflectionEntity) {
    try {
      System.out.println(
        "RequestBody: {" 
        + reflectionEntity.getUserID() + "," 
        + reflectionEntity.getDate() + ","
        + reflectionEntity.geActivity() + ","
        + reflectionEntity.getAchievement() + ","
        + reflectionEntity.getImprovementPoints() + "}");
      return reflectionService.createReflection(reflectionEntity);
    } catch (SQLException e) {
      System.out.println("SQLで例外が発生しました。");
      System.out.println(e.getMessage());
      return reflectionEntity;
    }
  }
}
