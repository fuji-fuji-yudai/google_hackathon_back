package com.example.google.google_hackathon.entity;

import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reflections",schema = "public")
public class ReflectionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long Id;
  @Column(nullable = false)
  private Long userId;
  @Column(nullable = false)
  private Date date;
  @Column(nullable = false)
  private String activity;
  @Column(nullable = false)
  private String achievement;
  @Column(nullable = false)
  private String improvementPoints;

  // コンストラクタ
  public ReflectionEntity() {}

  // コンストラクタ（全セット用）
  public ReflectionEntity(
    Long Id, Long userId, Date date, String activity, 
    String achievement, String improvementPoints) {
      this.userId = userId;
      this.date = date;
      this.activity = activity;
      this.achievement = achievement;
      this.improvementPoints = improvementPoints;
  }

  public Long getId() {
    return Id;
  }
  
  public Long getUserId() {
    return userId;
  }

  public Date getDate() {
    return date;
  }

  public String geActivity() {
    return activity;
  }

  public String getAchievement() {
    return achievement;
  }

  public String getImprovementPoints() {
    return improvementPoints;
  }

  public void setId(Long Id) {
    this.Id = Id;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public void setActivity(String activity) {
    this.activity = activity;
  }

  public void setAchievement(String achievement) {
    this.achievement = achievement;
  }

  public void setImprovementPoints(String improvementPoints) {
    this.improvementPoints = improvementPoints;
  }
}
