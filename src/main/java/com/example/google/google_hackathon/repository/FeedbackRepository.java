package com.example.google.google_hackathon.repository;
import com.example.google.google_hackathon.entity.FeedbackEntity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {
  Optional<FeedbackEntity> findByReflectionId(Long reflectionId);
}