package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks", schema = "public")
public class FeedbackEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long reflectionId; // ReflectionデータのIDを紐付け

  @Column(nullable = false, length = 1000)
  private String feedback; // 生成されたフィードバック

  @Column(nullable = false)
  private LocalDateTime createdAt; // フィードバック生成日時

  // コンストラクタ
  public FeedbackEntity() {}

  public FeedbackEntity(Long reflectionId, String feedback, LocalDateTime createdAt) {
    this.reflectionId = reflectionId;
    this.feedback = feedback;
    this.createdAt = createdAt;
  }

  // ゲッターとセッター
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getReflectionId() {
    return reflectionId;
  }

  public void setReflectionId(Long reflectionId) {
    this.reflectionId = reflectionId;
  }

  public String getFeedback() {
    return feedback;
  }

  public void setFeedback(String feedback) {
    this.feedback = feedback;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
