package com.example.google.google_hackathon.repository;

import java.sql.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.google.google_hackathon.entity.ReflectionEntity;

public interface ReflectionRepository extends JpaRepository<ReflectionEntity, Long> {
  ReflectionEntity findByUserIdAndDate(Long userId, Date date);
  List<ReflectionEntity> findByUserIdAndDateBetween(Long userId, Date startDate, Date endDate);
}