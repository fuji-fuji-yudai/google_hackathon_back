package com.example.google.google_hackathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.google.google_hackathon.entity.ReflectionSummaryEntity;

public interface ReflectionSummaryRepository extends JpaRepository<ReflectionSummaryEntity, Long> {
  ReflectionSummaryEntity findByUserIdAndYearMonth(Long userId, String yearMonth);

  
@Query("SELECT r FROM ReflectionSummaryEntity r WHERE r.userId = :userId AND r.yearMonth BETWEEN :startYm AND :endYm ORDER BY r.yearMonth DESC")
List<ReflectionSummaryEntity> findByUserIdAndYearMonthRange(
@Param("userId") Long userId,
@Param("startYm") String startYm,
@Param("endYm") String endYm

);

}
