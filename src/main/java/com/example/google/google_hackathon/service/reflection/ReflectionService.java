package com.example.google.google_hackathon.service.reflection;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.repository.ReflectionRepository;

@Service
public class ReflectionService {
  private final ReflectionRepository reflectionRepository;
  @Autowired
  public ReflectionService(ReflectionRepository reflectionRepository) {
    this.reflectionRepository = reflectionRepository;
  }
  public ReflectionEntity createReflection(ReflectionEntity reflectionEntity) throws SQLException {
    System.out.println("登録処理開始");
    return reflectionRepository.save(reflectionEntity);
  }
}
