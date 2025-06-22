package com.example.google.google_hackathon.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.google.google_hackathon.entity.ReflectionSummaryEntity;

public interface ReflectionSummaryRepository extends JpaRepository<ReflectionSummaryEntity, Long> {
  ReflectionSummaryEntity findByUserIdAndYearMonth(Long userId, String yearMonth);
}
