package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.RoadmapCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoadmapCategoryRepository extends JpaRepository<RoadmapCategory, Long> {
    // user_id と name でRoadmapCategoryを検索するメソッド
    Optional<RoadmapCategory> findByUser_IdAndName(Long userId, String name);
}
